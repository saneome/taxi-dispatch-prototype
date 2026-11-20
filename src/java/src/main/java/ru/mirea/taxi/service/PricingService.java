package ru.mirea.taxi.service;

import org.springframework.stereotype.Service;
import ru.mirea.taxi.dto.PriceEstimateDto;
import ru.mirea.taxi.model.Tariff;
import ru.mirea.taxi.repository.TariffRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {

    private final TariffRepository tariffRepository;

    public PricingService(TariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    /**
     * Алгоритмический расчёт динамической стоимости поездки
     * C = max(C_min, (C_base + d_km * P_km + t_min * P_min) * K_surge * K_weather)
     */
    public PriceEstimateDto calculatePrice(String tariffCode, int distanceMeters, int durationSeconds, double surgeMultiplier) {
        Tariff tariff = tariffRepository.findById(tariffCode)
                .orElseGet(() -> {
                    Tariff def = new Tariff();
                    def.setCode("ECONOMY");
                    def.setName("Эконом");
                    def.setBasePrice(new BigDecimal("149.00"));
                    def.setPricePerKm(new BigDecimal("16.00"));
                    def.setPricePerMinute(new BigDecimal("7.00"));
                    def.setMinPrice(new BigDecimal("199.00"));
                    return def;
                });

        double distanceKm = distanceMeters / 1000.0;
        double durationMinutes = durationSeconds / 60.0;

        BigDecimal kmCost = tariff.getPricePerKm().multiply(BigDecimal.valueOf(distanceKm));
        BigDecimal minCost = tariff.getPricePerMinute().multiply(BigDecimal.valueOf(durationMinutes));

        BigDecimal subtotal = tariff.getBasePrice().add(kmCost).add(minCost);

        // Применяем коэффициент повышенного спроса (surge)
        BigDecimal surgeFactor = BigDecimal.valueOf(Math.max(1.0, surgeMultiplier));
        BigDecimal priceWithSurge = subtotal.multiply(surgeFactor);

        // Стоимость не может быть ниже минимальной стоимости тарифа
        BigDecimal finalPrice = priceWithSurge.max(tariff.getMinPrice())
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        PriceEstimateDto dto = new PriceEstimateDto();
        dto.setTariffCode(tariff.getCode());
        dto.setTariffName(tariff.getName());
        dto.setDistanceMeters(distanceMeters);
        dto.setDurationSeconds(durationSeconds);
        dto.setEstimatedPrice(finalPrice);
        dto.setSurgeMultiplier(surgeFactor.setScale(2, RoundingMode.HALF_UP));
        dto.setBasePrice(tariff.getBasePrice());
        dto.setKmPrice(tariff.getPricePerKm());
        dto.setMinPrice(tariff.getMinPrice());
        return dto;
    }
}
