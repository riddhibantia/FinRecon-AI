package com.finrecon.reconciliation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// P3 read view of settlements (table owned by V1, written by ingestion).
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_id", nullable = false, updatable = false)
    private UUID settlementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "settled_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal feeAmount;

    // V5 stores ISO codes as VARCHAR(3); the CHECK keeps the shape.
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "settlement_status", nullable = false)
    private String settlementStatus;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "batch_id", nullable = false)
    private String batchId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Settlement() {
    }

    public Settlement(Payment payment, BigDecimal settledAmount, BigDecimal feeAmount,
                      String currency, String settlementStatus, LocalDate settlementDate,
                      String batchId) {
        this.payment = payment;
        this.settledAmount = settledAmount;
        this.feeAmount = feeAmount;
        this.currency = currency;
        this.settlementStatus = settlementStatus;
        this.settlementDate = settlementDate;
        this.batchId = batchId;
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public Payment getPayment() {
        return payment;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getSettlementStatus() {
        return settlementStatus;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public String getBatchId() {
        return batchId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
