package ru.mirea.taxi.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tariffs")
public class Tariff {
    @Id
    @Column(length = 50)
    private String code; // ECONOMY, COFORT, BUSINESS

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "price_per_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerKm;

    @Column(name = "price_per_minute", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerMinute;

    @Column(name = "min_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal minPrice;

    public Tariff() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public BigDecimal getPricePerKm() { return pricePerKm; }
    public void setPricePerKm(BigDecimal pricePerKm) { this.pricePerKm = pricePerKm; }

    public BigDecimal getPricePerMinute() { return pricePerMinute; }
    public void setPricePerMinute(BigDecimal pricePerMinute) { this.pricePerMinute = pricePerMinute; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
}
