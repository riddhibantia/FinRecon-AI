package com.finrecon.shared.domain;

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

// P4 entity for exceptions (V3 migration). Named ReconException because
// `Exception` would shadow java.lang.Exception. Lifecycle:
// OPEN -> INVESTIGATING -> RESOLVED / ESCALATED (master #2.3).
@Entity
@Table(name = "exceptions")
public class ReconException {

    public static final String OPEN = "OPEN";
    public static final String INVESTIGATING = "INVESTIGATING";
    public static final String RESOLVED = "RESOLVED";
    public static final String ESCALATED = "ESCALATED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exception_id", nullable = false, updatable = false)
    private UUID exceptionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private ReconciliationResult result;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected ReconException() {
    }

    public ReconException(ReconciliationResult result, String category, String severity) {
        this.result = result;
        this.category = category;
        this.severity = severity;
        this.status = OPEN;
    }

    public UUID getExceptionId() {
        return exceptionId;
    }

    public ReconciliationResult getResult() {
        return result;
    }

    public String getCategory() {
        return category;
    }

    public String getSeverity() {
        return severity;
    }

    public String getStatus() {
        return status;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void assign(String analystId) {
        requireTransition(OPEN, "assign");
        this.assignedTo = analystId;
        this.status = INVESTIGATING;
    }

    public void resolve() {
        requireTransition(INVESTIGATING, "resolve");
        this.status = RESOLVED;
        this.resolvedAt = OffsetDateTime.now();
    }

    public void escalate() {
        if (RESOLVED.equals(status) || ESCALATED.equals(status)) {
            throw new IllegalStateException("Cannot escalate a case in status " + status);
        }
        this.status = ESCALATED;
        this.resolvedAt = OffsetDateTime.now();
    }

    private void requireTransition(String expected, String action) {
        if (!expected.equals(status)) {
            throw new IllegalStateException(
                    "Cannot " + action + " a case in status " + status);
        }
    }

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
