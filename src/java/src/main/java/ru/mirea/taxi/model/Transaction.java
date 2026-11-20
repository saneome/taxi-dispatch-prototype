package ru.mirea.taxi.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "platform_commission", nullable = false, precision = 10, scale = 2)
    private BigDecimal platformCommission;

    @Column(name = "driver_payout", nullable = false, precision = 10, scale = 2)
    private BigDecimal driverPayout;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus = "CAPTURED"; // HOLD, CAPTURED, REFUNDED

    @Column(name = "hold_id")
    private String holdId;

    @Column(name = "fiscal_receipt_url")
    private String fiscalReceiptUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Transaction() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPlatformCommission() { return platformCommission; }
    public void setPlatformCommission(BigDecimal platformCommission) { this.platformCommission = platformCommission; }

    public BigDecimal getDriverPayout() { return driverPayout; }
    public void setDriverPayout(BigDecimal driverPayout) { this.driverPayout = driverPayout; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getHoldId() { return holdId; }
    public void setHoldId(String holdId) { this.holdId = holdId; }

    public String getFiscalReceiptUrl() { return fiscalReceiptUrl; }
    public void setFiscalReceiptUrl(String fiscalReceiptUrl) { this.fiscalReceiptUrl = fiscalReceiptUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
