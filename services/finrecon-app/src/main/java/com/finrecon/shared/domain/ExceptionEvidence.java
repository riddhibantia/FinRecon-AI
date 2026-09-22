package com.finrecon.shared.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// P4 entity for exception_evidence (V3 migration). One row per compared
// field: what was expected, what was observed, and the source record.
@Entity
@Table(name = "exception_evidence")
public class ExceptionEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evidence_id", nullable = false, updatable = false)
    private UUID evidenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exception_id", nullable = false)
    private ReconException exception;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_record_id", nullable = false)
    private String sourceRecordId;

    @Column(name = "field_name")
    private String fieldName;

    @Column(name = "expected_value")
    private String expectedValue;

    @Column(name = "observed_value")
    private String observedValue;

    protected ExceptionEvidence() {
    }

    public ExceptionEvidence(ReconException exception, String sourceType,
                             String sourceRecordId, String fieldName,
                             String expectedValue, String observedValue) {
        this.exception = exception;
        this.sourceType = sourceType;
        this.sourceRecordId = sourceRecordId;
        this.fieldName = fieldName;
        this.expectedValue = expectedValue;
        this.observedValue = observedValue;
    }

    public UUID getEvidenceId() {
        return evidenceId;
    }

    public ReconException getException() {
        return exception;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceRecordId() {
        return sourceRecordId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public String getObservedValue() {
        return observedValue;
    }
}
