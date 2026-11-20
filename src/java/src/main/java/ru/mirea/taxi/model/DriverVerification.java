package ru.mirea.taxi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_verifications")
public class DriverVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "fgis_status", nullable = false)
    private String fgisStatus = "VALID"; // VALID, REJECTED, EXPIRED

    @Column(name = "fgis_check_message")
    private String fgisCheckMessage;

    @Column(name = "osgop_policy_number", nullable = false)
    private String osgopPolicyNumber;

    @Column(name = "osgop_status", nullable = false)
    private String osgopStatus = "VALID";

    @Column(name = "criminal_record_check", nullable = false)
    private String criminalRecordCheck = "CLEAN";

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt = LocalDateTime.now();

    public DriverVerification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }

    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }

    public String getFgisStatus() { return fgisStatus; }
    public void setFgisStatus(String fgisStatus) { this.fgisStatus = fgisStatus; }

    public String getFgisCheckMessage() { return fgisCheckMessage; }
    public void setFgisCheckMessage(String fgisCheckMessage) { this.fgisCheckMessage = fgisCheckMessage; }

    public String getOsgopPolicyNumber() { return osgopPolicyNumber; }
    public void setOsgopPolicyNumber(String osgopPolicyNumber) { this.osgopPolicyNumber = osgopPolicyNumber; }

    public String getOsgopStatus() { return osgopStatus; }
    public void setOsgopStatus(String osgopStatus) { this.osgopStatus = osgopStatus; }

    public String getCriminalRecordCheck() { return criminalRecordCheck; }
    public void setCriminalRecordCheck(String criminalRecordCheck) { this.criminalRecordCheck = criminalRecordCheck; }

    public LocalDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}
