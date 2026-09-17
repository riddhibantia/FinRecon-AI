package com.finrecon.exceptioncase.domain;

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

// P4 entity for resolution_actions (V3 migration). Every assign, resolve,
// and escalate writes one row: who did what to the case and why.
@Entity
@Table(name = "resolution_actions")
public class ResolutionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "action_id", nullable = false, updatable = false)
    private UUID actionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exception_id", nullable = false)
    private ReconException exception;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ResolutionAction() {
    }

    public ResolutionAction(ReconException exception, String actionType,
                            String actorType, String actorId, String notes) {
        this.exception = exception;
        this.actionType = actionType;
        this.actorType = actorType;
        this.actorId = actorId;
        this.notes = notes;
    }

    public UUID getActionId() {
        return actionId;
    }

    public ReconException getException() {
        return exception;
    }

    public String getActionType() {
        return actionType;
    }

    public String getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getNotes() {
        return notes;
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
