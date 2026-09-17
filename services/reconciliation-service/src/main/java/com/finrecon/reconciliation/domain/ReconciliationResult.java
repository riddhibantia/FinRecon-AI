package com.finrecon.reconciliation.domain;

import java.math.BigDecimal;
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

// P3 entity for reconciliation_results (V2 migration). One row per
// (run, payment); match_status is MATCHED or MISMATCHED and mismatch_type
// uses the master taxonomy vocabulary for P4 to consume.
@Entity
@Table(name = "reconciliation_results")
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "result_id", nullable = false, updatable = false)
    private UUID resultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "match_status", nullable = false)
    private String matchStatus;

    @Column(name = "mismatch_type")
    private String mismatchType;

    @Column(name = "amount_difference", precision = 18, scale = 2)
    private BigDecimal amountDifference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ReconciliationResult() {
    }

    public ReconciliationResult(ReconciliationRun run, Payment payment,
                                String matchStatus, String mismatchType,
                                BigDecimal amountDifference) {
        this.run = run;
        this.payment = payment;
        this.matchStatus = matchStatus;
        this.mismatchType = mismatchType;
        this.amountDifference = amountDifference;
    }

    public UUID getResultId() {
        return resultId;
    }

    public ReconciliationRun getRun() {
        return run;
    }

    public Payment getPayment() {
        return payment;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public String getMismatchType() {
        return mismatchType;
    }

    public BigDecimal getAmountDifference() {
        return amountDifference;
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
