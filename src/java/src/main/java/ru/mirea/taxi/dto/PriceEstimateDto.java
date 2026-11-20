package ru.mirea.taxi.dto;

import java.math.BigDecimal;

public class PriceEstimateDto {
    private String tariffCode;
    private String tariffName;
    private Integer distanceMeters;
    private Integer durationSeconds;
    private BigDecimal estimatedPrice;
    private BigDecimal surgeMultiplier;
    private BigDecimal basePrice;
    private BigDecimal kmPrice;
    private BigDecimal minPrice;

    public PriceEstimateDto() {}

    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }

    public String getTariffName() { return tariffName; }
    public void setTariffName(String tariffName) { this.tariffName = tariffName; }

    public Integer getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public BigDecimal getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(BigDecimal estimatedPrice) { this.estimatedPrice = estimatedPrice; }

    public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
    public void setSurgeMultiplier(BigDecimal surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public BigDecimal getKmPrice() { return kmPrice; }
    public void setKmPrice(BigDecimal kmPrice) { this.kmPrice = kmPrice; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
}
