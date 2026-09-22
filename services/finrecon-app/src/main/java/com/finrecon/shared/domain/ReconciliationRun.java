package com.finrecon.shared.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// P3 entity for reconciliation_runs (V2 migration). One row per
// deterministic engine run; rule_version ties results to engine code.
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "source_set", nullable = false)
    private String sourceSet;

    @Column(name = "started_at", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "rule_version", nullable = false, updatable = false)
    private String ruleVersion;

    protected ReconciliationRun() {
    }

    public ReconciliationRun(String sourceSet, String ruleVersion) {
        this.sourceSet = sourceSet;
        this.ruleVersion = ruleVersion;
        this.status = "STARTED";
    }

    public UUID getRunId() {
        return runId;
    }

    public String getSourceSet() {
        return sourceSet;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void complete() {
        this.status = "COMPLETED";
        this.completedAt = OffsetDateTime.now();
    }

    public void fail() {
        this.status = "FAILED";
        this.completedAt = OffsetDateTime.now();
    }

    @PrePersist
    protected void prePersist() {
        if (startedAt == null) {
            startedAt = OffsetDateTime.now();
        }
    }
}
