package ru.mirea.taxi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.mirea.taxi.dto.PriceEstimateDto;
import ru.mirea.taxi.model.Tariff;
import ru.mirea.taxi.repository.TariffRepository;
import ru.mirea.taxi.service.PricingService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private TariffRepository tariffRepository;
    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        tariffRepository = Mockito.mock(TariffRepository.class);
        pricingService = new PricingService(tariffRepository);

        Tariff economy = new Tariff();
        economy.setCode("ECONOMY");
        economy.setName("Эконом");
        economy.setBasePrice(new BigDecimal("149.00"));
        economy.setPricePerKm(new BigDecimal("16.00"));
        economy.setPricePerMinute(new BigDecimal("7.00"));
        economy.setMinPrice(new BigDecimal("199.00"));

        when(tariffRepository.findById(eq("ECONOMY"))).thenReturn(Optional.of(economy));
    }

    @Test
    @DisplayName("Проверка расчета динамического тарифа по формуле")
    void testCalculatePriceNormal() {
        // 5 км (5000 м), 10 минут (600 с), surge = 1.0
        // Стоимость = 149 (база) + 5 * 16 (80) + 10 * 7 (70) = 299 руб.
        PriceEstimateDto estimate = pricingService.calculatePrice("ECONOMY", 5000, 600, 1.0);

        assertNotNull(estimate);
        assertEquals("ECONOMY", estimate.getTariffCode());
        assertEquals(new BigDecimal("299.00"), estimate.getEstimatedPrice());
    }

    @Test
    @DisplayName("Проверка соблюдения минимальной стоимости тарифа (minPrice)")
    void testCalculatePriceMinLimit() {
        // Короткая поездка: 0.5 км (500 м), 1 минута (60 с)
        // 149 + 8 + 7 = 164 руб, но minPrice = 199 руб.
        PriceEstimateDto estimate = pricingService.calculatePrice("ECONOMY", 500, 60, 1.0);

        assertNotNull(estimate);
        assertEquals(new BigDecimal("199.00"), estimate.getEstimatedPrice());
    }

    @Test
    @DisplayName("Проверка коэффициента повышенного спроса (surge = 1.5)")
    void testCalculatePriceWithSurge() {
        // 5 км, 10 мин = 299 руб * 1.5 = 448.50 -> 449 руб.
        PriceEstimateDto estimate = pricingService.calculatePrice("ECONOMY", 5000, 600, 1.5);

        assertNotNull(estimate);
        assertEquals(new BigDecimal("449.00"), estimate.getEstimatedPrice());
    }
}
