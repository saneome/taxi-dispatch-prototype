package ru.mirea.taxi.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "carriers")
public class Carrier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(nullable = false, unique = true, length = 12)
    private String inn;

    @Column(length = 15)
    private String ogrn;

    @Column(name = "carrier_type", nullable = false)
    private String carrierType; // LEGAL_ENTITY, INDIVIDUAL_ENTREPRENEUR, SELF_EMPLOYED

    @Column(name = "registry_number", nullable = false)
    private String registryNumber;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Carrier() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getInn() { return inn; }
    public void setInn(String inn) { this.inn = inn; }

    public String getOgrn() { return ogrn; }
    public void setOgrn(String ogrn) { this.ogrn = ogrn; }

    public String getCarrierType() { return carrierType; }
    public void setCarrierType(String carrierType) { this.carrierType = carrierType; }

    public String getRegistryNumber() { return registryNumber; }
    public void setRegistryNumber(String registryNumber) { this.registryNumber = registryNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
