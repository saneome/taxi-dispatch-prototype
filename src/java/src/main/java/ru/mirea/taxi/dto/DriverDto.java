package ru.mirea.taxi.dto;

import java.math.BigDecimal;

public class DriverDto {
    private Long id;
    private String fullName;
    private String phone;
    private String carMake;
    private String carModel;
    private String licensePlate;
    private String formattedPlate;
    private String regionLabel;
    private String color;
    private String tariffCategory;
    private BigDecimal rating;
    private String status;
    private Double lat;
    private Double lon;
    private String fgisStatus;
    private String fgisRegistryNumber;
    private String osgopPolicyNumber;
    private Integer etaSeconds;
    private Integer distanceMeters;
    private Double distanceKm;
    private Double matchingScore;

    public DriverDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCarMake() { return carMake; }
    public void setCarMake(String carMake) { this.carMake = carMake; }

    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getFormattedPlate() { return formattedPlate; }
    public void setFormattedPlate(String formattedPlate) { this.formattedPlate = formattedPlate; }

    public String getRegionLabel() { return regionLabel; }
    public void setRegionLabel(String regionLabel) { this.regionLabel = regionLabel; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getTariffCategory() { return tariffCategory; }
    public void setTariffCategory(String tariffCategory) { this.tariffCategory = tariffCategory; }

    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getFgisStatus() { return fgisStatus; }
    public void setFgisStatus(String fgisStatus) { this.fgisStatus = fgisStatus; }

    public String getFgisRegistryNumber() { return fgisRegistryNumber; }
    public void setFgisRegistryNumber(String fgisRegistryNumber) { this.fgisRegistryNumber = fgisRegistryNumber; }

    public String getOsgopPolicyNumber() { return osgopPolicyNumber; }
    public void setOsgopPolicyNumber(String osgopPolicyNumber) { this.osgopPolicyNumber = osgopPolicyNumber; }

    public Integer getEtaSeconds() { return etaSeconds; }
    public void setEtaSeconds(Integer etaSeconds) { this.etaSeconds = etaSeconds; }

    public Integer getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }

    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }

    public Double getMatchingScore() { return matchingScore; }
    public void setMatchingScore(Double matchingScore) { this.matchingScore = matchingScore; }
}
