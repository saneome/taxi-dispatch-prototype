package ru.mirea.taxi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.mirea.taxi.dto.DriverDto;
import ru.mirea.taxi.dto.RouteDto;
import ru.mirea.taxi.model.Driver;
import ru.mirea.taxi.model.DriverVerification;
import ru.mirea.taxi.model.Vehicle;
import ru.mirea.taxi.repository.DriverRepository;
import ru.mirea.taxi.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MatchingService {
    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final FgisVerificationService fgisVerificationService;
    private final OsrmRoutingService routingService;
    private final PlateService plateService;
    private final double maxSearchRadiusKm;
    private final DemoFleetService demoFleetService;

    public MatchingService(
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            FgisVerificationService fgisVerificationService,
            OsrmRoutingService routingService,
            PlateService plateService,
            DemoFleetService demoFleetService,
            @Value("${taxi.search-radius-km:35.0}") double maxSearchRadiusKm) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.fgisVerificationService = fgisVerificationService;
        this.routingService = routingService;
        this.plateService = plateService;
        this.demoFleetService = demoFleetService;
        this.maxSearchRadiusKm = maxSearchRadiusKm;
    }

    /**
     * Поиск и ранжирование доступных водителей вокруг точки подачи пассажира
     */
    public List<DriverDto> findAndRankCandidates(double pickupLat, double pickupLon, String requestedTariff) {
        demoFleetService.ensureLocalFleet(pickupLat, pickupLon, Math.min(10, maxSearchRadiusKm));
        List<Driver> availableDrivers = driverRepository.findByStatus("AVAILABLE");
        List<DriverDto> candidates = new ArrayList<>();

        for (Driver driver : availableDrivers) {
            if (!DemoFleetService.isNear(driver, pickupLat, pickupLon, maxSearchRadiusKm)) continue;
            // 1. Проверка по ФЗ-580
            if (!fgisVerificationService.verifyDriverForOrder(driver)) {
                log.info("Driver {} failed FGIS/OSGOP verification, skipping", driver.getId());
                continue;
            }

            // 2. Получение автомобиля и категории
            Vehicle vehicle = vehicleRepository.findByDriverId(driver.getId()).orElse(null);
            if (vehicle == null) continue;

            // Фильтрация по тарифу (если указан)
            if (requestedTariff != null && !requestedTariff.isEmpty()) {
                if (!isTariffCompatible(vehicle.getTariffCategory(), requestedTariff)) {
                    continue;
                }
            }

            // 3. Расчёт расстояния подачи
            RouteDto routeToPickup = routingService.calculateRoute(
                    driver.getCurrentLat(), driver.getCurrentLon(),
                    pickupLat, pickupLon);

            double distanceKm = routeToPickup.getDistanceMeters() / 1000.0;
            if (distanceKm > maxSearchRadiusKm) {
                continue;
            }

            DriverVerification ver = fgisVerificationService.getVerificationDetails(driver.getId());

            // 4. Взвешенный скоринг кандидата (Matching Score)
            // S = 0.50 * (1 - norm(ETA)) + 0.35 * (rating / 5.0) + 0.15 * compliance
            double etaMinutes = routeToPickup.getDurationSeconds() / 60.0;
            double normEta = Math.max(0.0, 1.0 - (etaMinutes / 20.0));
            double ratingFactor = driver.getRating().doubleValue() / 5.0;
            double complianceScore = "VALID".equals(ver.getFgisStatus()) ? 1.0 : 0.0;

            double score = 0.50 * normEta + 0.35 * ratingFactor + 0.15 * complianceScore;

            DriverDto dto = new DriverDto();
            dto.setId(driver.getId());
            dto.setFullName(driver.getFullName());
            dto.setPhone(driver.getPhone());
            dto.setCarMake(vehicle.getMake());
            dto.setCarModel(vehicle.getModel());
            dto.setLicensePlate(vehicle.getLicensePlate());
            dto.setFormattedPlate(plateService.formatPlate(vehicle.getLicensePlate()));
            dto.setRegionLabel(plateService.regionLabelOf(vehicle.getLicensePlate()));
            dto.setColor(vehicle.getColor());
            dto.setTariffCategory(vehicle.getTariffCategory());
            dto.setRating(driver.getRating());
            dto.setStatus(driver.getStatus());
            dto.setLat(driver.getCurrentLat());
            dto.setLon(driver.getCurrentLon());
            dto.setFgisStatus(ver.getFgisStatus());
            dto.setFgisRegistryNumber(driver.getCarrier() != null ? driver.getCarrier().getRegistryNumber() : "RU-77-ACTIVE");
            dto.setOsgopPolicyNumber(ver.getOsgopPolicyNumber());
            dto.setDistanceMeters(routeToPickup.getDistanceMeters());
            dto.setEtaSeconds(routeToPickup.getDurationSeconds());
            dto.setMatchingScore(Math.round(score * 100.0) / 100.0);

            candidates.add(dto);
        }

        // Ранжирование по убыванию matching score (лучший кандидат сверху)
        candidates.sort(Comparator.comparingDouble(DriverDto::getMatchingScore).reversed());

        return candidates;
    }

    private boolean isTariffCompatible(String vehicleCategory, String requestedTariff) {
        if ("ECONOMY".equalsIgnoreCase(requestedTariff)) return true;
        if ("COMFORT".equalsIgnoreCase(requestedTariff)) {
            return "COMFORT".equalsIgnoreCase(vehicleCategory) || "BUSINESS".equalsIgnoreCase(vehicleCategory);
        }
        if ("BUSINESS".equalsIgnoreCase(requestedTariff)) {
            return "BUSINESS".equalsIgnoreCase(vehicleCategory);
        }
        return true;
    }

    public boolean canServePickup(Driver driver, double lat, double lon, String tariff) {
        if (driver == null || !"AVAILABLE".equals(driver.getStatus())
                || !DemoFleetService.isNear(driver, lat, lon, maxSearchRadiusKm)) return false;
        Vehicle vehicle = vehicleRepository.findByDriverId(driver.getId()).orElse(null);
        if (vehicle == null || !isTariffCompatible(vehicle.getTariffCategory(), tariff)
                || !fgisVerificationService.verifyDriverForOrder(driver)) return false;
        RouteDto approach = routingService.calculateRoute(driver.getCurrentLat(), driver.getCurrentLon(), lat, lon);
        return approach.getDistanceKm() <= maxSearchRadiusKm;
    }
}
