package com.zorvyn.finance.entity;
import com.zorvyn.finance.entity.enums.AuditAction;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable audit trail entry.
 *
 * Every meaningful state change in the system writes one row here.
 * This table is append-only — rows are never updated or deleted.
 *
 * Design choices:
 *
 * 1. Does NOT extend BaseEntity. AuditLog has its own timestamp (createdAt)
 *    but needs no updatedAt — it is immutable by design.
 *    Extending BaseEntity would add an updatedAt column that is conceptually
 *    wrong for an immutable audit record.
 *
 * 2. oldValue / newValue store JSON snapshots as plain text. This avoids
 *    a separate schema for audit data and keeps the audit service simple.
 *    The service serialises the before/after state to JSON before writing.
 *
 * 3. actor is nullable (via actorId) to handle system-generated events
 *    where no authenticated user is present (e.g. scheduled jobs).
 *
 * 4. entityType + entityId form a generic reference to whatever was changed
 *    (a FinancialRecord, a User, etc.) without requiring FK constraints that
 *    would break when those records are soft-deleted or purged.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * The user who triggered the action.
     * Stored as a plain UUID column (not a FK) so the audit log
     * survives even if the user record is later removed.
     */
    @Column(name = "actor_id")
    private UUID actorId;

    /** Human-readable name captured at write time — user may rename later. */
    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    /** Simple class name of the affected entity, e.g. "FinancialRecord". */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    /** JSON snapshot of the entity state before the change. Null on CREATE. */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** JSON snapshot of the entity state after the change. Null on DELETE. */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ----------------------------------------------------------------
    // Constructors
    // ----------------------------------------------------------------

    protected AuditLog() {}

    /**
     * Factory-style constructor — all fields supplied at creation.
     * createdAt is set here rather than via JPA auditing because
     * this entity does not extend BaseEntity.
     */
    public AuditLog(UUID actorId, String actorName, AuditAction action,
                    String entityType, UUID entityId,
                    String oldValue, String newValue, String ipAddress) {
        this.actorId    = actorId;
        this.actorName  = actorName;
        this.action     = action;
        this.entityType = entityType;
        this.entityId   = entityId;
        this.oldValue   = oldValue;
        this.newValue   = newValue;
        this.ipAddress  = ipAddress;
        this.createdAt  = LocalDateTime.now();
    }

    // ----------------------------------------------------------------
    // Getters — no setters: audit logs are immutable after creation
    // ----------------------------------------------------------------

    public UUID getId()           { return id; }
    public UUID getActorId()      { return actorId; }
    public String getActorName()  { return actorName; }
    public AuditAction getAction(){ return action; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId()     { return entityId; }
    public String getOldValue()   { return oldValue; }
    public String getNewValue()   { return newValue; }
    public String getIpAddress()  { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}