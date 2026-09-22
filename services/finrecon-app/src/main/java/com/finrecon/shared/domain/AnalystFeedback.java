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

// FR-11 human feedback (V4 analyst_feedback). Append-only: an analyst
// confirms or corrects the classification; the original value is retained.
@Entity
@Table(name = "analyst_feedback")
public class AnalystFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "feedback_id", nullable = false, updatable = false)
    private UUID feedbackId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exception_id", nullable = false, updatable = false)
    private ReconException exception;

    @Column(name = "analyst_id", nullable = false, updatable = false)
    private String analystId;

    @Column(name = "original_value", updatable = false)
    private String originalValue;

    @Column(name = "corrected_value", updatable = false)
    private String correctedValue;

    @Column(name = "reason", updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AnalystFeedback() {
    }

    public AnalystFeedback(ReconException exception, String analystId,
                           String originalValue, String correctedValue, String reason) {
        this.exception = exception;
        this.analystId = analystId;
        this.originalValue = originalValue;
        this.correctedValue = correctedValue;
        this.reason = reason;
    }

    public UUID getFeedbackId() {
        return feedbackId;
    }

    public String getAnalystId() {
        return analystId;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public String getCorrectedValue() {
        return correctedValue;
    }

    public String getReason() {
        return reason;
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