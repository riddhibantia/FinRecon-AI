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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// P4 audit primitive (V4 audit_logs). Append-only: case opened, assigned,
// resolved, escalated. No updates or deletes through this service.
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private String entityId;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private OffsetDateTime timestamp;

    // Raw JSON text. The JSON type code keeps Postgres (jsonb) and H2
    // (JSON) happy without an extra mapping library.
    @Column(name = "metadata", nullable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    protected AuditLog() {
    }

    public AuditLog(String actorType, String actorId, String action,
                    String entityType, String entityId, String metadata) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.metadata = metadata == null ? "{}" : metadata;
    }

    public UUID getAuditId() {
        return auditId;
    }

    public String getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public String getMetadata() {
        return metadata;
    }

    @PrePersist
    protected void prePersist() {
        if (timestamp == null) {
            timestamp = OffsetDateTime.now();
        }
    }
}
