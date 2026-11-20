package ru.mirea.taxi.dto;

public class CreateOrderRequest {
    private String passengerName = "Демо-пассажир";
    private String passengerPhone = "+7 (999) 000-11-22";
    private String fromAddress;
    private String toAddress;
    private Double pickupLat;
    private Double pickupLon;
    private Double dropoffLat;
    private Double dropoffLon;
    private String tariffCode = "COMFORT";
    private Long driverId;

    public CreateOrderRequest() {}

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getPassengerName() { return passengerName; }
    public void setPassengerName(String passengerName) { this.passengerName = passengerName; }

    public String getPassengerPhone() { return passengerPhone; }
    public void setPassengerPhone(String passengerPhone) { this.passengerPhone = passengerPhone; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public Double getPickupLat() { return pickupLat; }
    public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }

    public Double getPickupLon() { return pickupLon; }
    public void setPickupLon(Double pickupLon) { this.pickupLon = pickupLon; }

    public Double getDropoffLat() { return dropoffLat; }
    public void setDropoffLat(Double dropoffLat) { this.dropoffLat = dropoffLat; }

    public Double getDropoffLon() { return dropoffLon; }
    public void setDropoffLon(Double dropoffLon) { this.dropoffLon = dropoffLon; }

    public String getTariffCode() { return tariffCode; }
    public void setTariffCode(String tariffCode) { this.tariffCode = tariffCode; }

    public String getTariffCategory() { return tariffCode; }
    public void setTariffCategory(String tariffCategory) { this.tariffCode = tariffCategory; }
}
