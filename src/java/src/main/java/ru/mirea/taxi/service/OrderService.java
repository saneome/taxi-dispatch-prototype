package ru.mirea.taxi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.taxi.dto.*;
import ru.mirea.taxi.model.*;
import ru.mirea.taxi.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TariffRepository tariffRepository;
    private final TransactionRepository transactionRepository;
    private final OsrmRoutingService routingService;
    private final PricingService pricingService;
    private final MatchingService matchingService;
    private final FgisVerificationService fgisVerificationService;
    private final BigDecimal commissionRate;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            TariffRepository tariffRepository,
            TransactionRepository transactionRepository,
            OsrmRoutingService routingService,
            PricingService pricingService,
            MatchingService matchingService,
            FgisVerificationService fgisVerificationService,
            @Value("${taxi.platform-commission-rate:0.18}") double commissionRate) {
        this.orderRepository = orderRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.tariffRepository = tariffRepository;
        this.transactionRepository = transactionRepository;
        this.routingService = routingService;
        this.pricingService = pricingService;
        this.matchingService = matchingService;
        this.fgisVerificationService = fgisVerificationService;
        this.commissionRate = BigDecimal.valueOf(commissionRate);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Создание заказа и полный цикл назначения
     */
    @Transactional
    public OrderResponse createAndDispatchOrder(CreateOrderRequest request) {
        // 1. Построение Mapbox маршрута поездки (точка А -> точка Б)
        RouteDto route = routingService.calculateRoute(
                request.getPickupLat(), request.getPickupLon(),
                request.getDropoffLat(), request.getDropoffLon());

        // 2. Расчет динамического тарифа
        PriceEstimateDto priceEstimate = pricingService.calculatePrice(
                request.getTariffCode(),
                route.getDistanceMeters(),
                route.getDurationSeconds(),
                1.15 // surge коэффициент в час-пик
        );

        Tariff tariff = tariffRepository.findById(request.getTariffCode())
                .orElse(null);

        Order order = new Order();
        order.setPassengerName(request.getPassengerName());
        order.setPassengerPhone(request.getPassengerPhone());
        order.setFromAddress(request.getFromAddress() != null ? request.getFromAddress() : "Точка отправления (А)");
        order.setToAddress(request.getToAddress() != null ? request.getToAddress() : "Точка назначения (Б)");
        order.setPickupLat(request.getPickupLat());
        order.setPickupLon(request.getPickupLon());
        order.setDropoffLat(request.getDropoffLat());
        order.setDropoffLon(request.getDropoffLon());
        order.setTariff(tariff);
        order.setDistanceMeters(route.getDistanceMeters());
        order.setDurationSeconds(route.getDurationSeconds());
        order.setEstimatedPrice(priceEstimate.getEstimatedPrice());
        order.setFinalPrice(priceEstimate.getEstimatedPrice());
        order.setSurgeMultiplier(priceEstimate.getSurgeMultiplier());

        // Сохраняем GeoJSON геометрию маршрута
        try {
            order.setRouteGeometry(objectMapper.writeValueAsString(route.getCoordinates()));
        } catch (JsonProcessingException e) {
            order.setRouteGeometry("[]");
        }

        // 3. Шаг BPMN 1: Холдирование суммы (Блокировка на карте)
        String holdId = "HOLD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        order.setHoldId(holdId);
        order.setStatus(OrderStatus.HOLD_FUNDS);
        order = orderRepository.save(order);

        // 4. Шаги BPMN 2-5: Поиск и назначение выбранного пользователем или ближайшего верифицированного водителя
        Driver assignedDriver = null;
        if (request.getDriverId() != null) {
            Driver requested = driverRepository.findById(request.getDriverId()).orElse(null);
            if (!matchingService.canServePickup(requested, request.getPickupLat(), request.getPickupLon(), request.getTariffCode())) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Selected driver is unavailable in the pickup area");
            }
            if (requested != null) {
                assignedDriver = requested;
                log.info("Assigned explicitly selected driver: {} ({})", assignedDriver.getId(), assignedDriver.getFullName());
            }
        }

        if (assignedDriver == null) {
            List<DriverDto> candidates = matchingService.findAndRankCandidates(
                    request.getPickupLat(), request.getPickupLon(), request.getTariffCode());
            if (!candidates.isEmpty()) {
                DriverDto bestCandidate = candidates.get(0);
                assignedDriver = driverRepository.findById(bestCandidate.getId()).orElse(null);
            }
        }

        if (assignedDriver != null) {
            assignedDriver.setStatus("BUSY");
            driverRepository.save(assignedDriver);

            Vehicle vehicle = vehicleRepository.findByDriverId(assignedDriver.getId()).orElse(null);
            order.setDriver(assignedDriver);
            order.setVehicle(vehicle);
            order.setStatus(OrderStatus.ASSIGNED);
            order = orderRepository.save(order);
        }

        return mapToResponse(order);
    }

    /**
     * Продвижение конечного автомата заказа (FSM)
     */
    @Transactional
    public OrderResponse advanceOrderStatus(Long orderId, String targetStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ " + orderId + " не найден"));

        OrderStatus newStatus = OrderStatus.valueOf(targetStatus.toUpperCase());
        order.setStatus(newStatus);

        if (newStatus == OrderStatus.COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
            // Освобождаем водителя
            if (order.getDriver() != null) {
                order.getDriver().setStatus("AVAILABLE");
                // Обновляем текущие координаты водителя в точку высадки
                order.getDriver().setCurrentLat(order.getDropoffLat());
                order.getDriver().setCurrentLon(order.getDropoffLon());
                driverRepository.save(order.getDriver());
            }

            // Создаем транзакцию сплитования по 54-ФЗ
            BigDecimal total = order.getFinalPrice() != null ? order.getFinalPrice() : order.getEstimatedPrice();
            BigDecimal platformPart = total.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal driverPart = total.subtract(platformPart);

            Transaction tx = new Transaction();
            tx.setOrder(order);
            tx.setTotalAmount(total);
            tx.setPlatformCommission(platformPart);
            tx.setDriverPayout(driverPart);
            tx.setPaymentStatus("CAPTURED");
            tx.setHoldId(order.getHoldId());
            tx.setFiscalReceiptUrl("https://ofd.nalog.ru/check/" + UUID.randomUUID());
            transactionRepository.save(tx);
        } else if (newStatus == OrderStatus.CANCELLED) {
            if (order.getDriver() != null) {
                order.getDriver().setStatus("AVAILABLE");
                driverRepository.save(order.getDriver());
            }
        }

        order = orderRepository.save(order);
        return mapToResponse(order);
    }

    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ " + orderId + " не найден"));
        return mapToResponse(order);
    }

    public List<OrderResponse> getRecentOrders() {
        return orderRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setStatus(order.getStatus().name());
        res.setStatusDescription(order.getStatus().getDescription());
        res.setPassengerName(order.getPassengerName());
        res.setPassengerPhone(order.getPassengerPhone());
        res.setFromAddress(order.getFromAddress());
        res.setToAddress(order.getToAddress());
        res.setPickupLat(order.getPickupLat());
        res.setPickupLon(order.getPickupLon());
        res.setDropoffLat(order.getDropoffLat());
        res.setDropoffLon(order.getDropoffLon());
        res.setDistanceMeters(order.getDistanceMeters());
        res.setDurationSeconds(order.getDurationSeconds());
        res.setEstimatedPrice(order.getEstimatedPrice());
        res.setFinalPrice(order.getFinalPrice());
        res.setHoldId(order.getHoldId());
        res.setRouteGeometry(order.getRouteGeometry());
        res.setCreatedAt(order.getCreatedAt());
        res.setCompletedAt(order.getCompletedAt());

        // Расчет финансовых долей
        BigDecimal total = order.getFinalPrice() != null ? order.getFinalPrice() : order.getEstimatedPrice();
        if (total != null) {
            BigDecimal comm = total.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
            res.setPlatformCommission(comm);
            res.setDriverPayout(total.subtract(comm));
        }

        if (order.getDriver() != null) {
            Driver d = order.getDriver();
            DriverDto dto = new DriverDto();
            dto.setId(d.getId());
            dto.setFullName(d.getFullName());
            dto.setPhone(d.getPhone());
            dto.setRating(d.getRating());
            dto.setStatus(d.getStatus());
            dto.setLat(d.getCurrentLat());
            dto.setLon(d.getCurrentLon());

            if (order.getVehicle() != null) {
                Vehicle v = order.getVehicle();
                dto.setCarMake(v.getMake());
                dto.setCarModel(v.getModel());
                dto.setLicensePlate(v.getLicensePlate());
                dto.setColor(v.getColor());
                dto.setTariffCategory(v.getTariffCategory());
            }

            DriverVerification ver = fgisVerificationService.getVerificationDetails(d.getId());
            if (ver != null) {
                dto.setFgisStatus(ver.getFgisStatus());
                dto.setOsgopPolicyNumber(ver.getOsgopPolicyNumber());
                dto.setFgisRegistryNumber(d.getCarrier() != null ? d.getCarrier().getRegistryNumber() : "RU-77-VALID");
            }
            res.setDriver(dto);
        }

        return res;
    }
}
