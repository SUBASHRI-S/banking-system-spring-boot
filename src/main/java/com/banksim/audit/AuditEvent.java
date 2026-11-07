package com.banksim.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** One immutable row of the audit trail. The database refuses UPDATE and DELETE on this table. */
@Entity
@Table(name = "audit_log")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private AuditOutcome outcome;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(updatable = false)
    private String target;

    @Column(updatable = false)
    private String detail;

    @Column(name = "request_id", updatable = false, length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(AuditEventType eventType, AuditOutcome outcome, Long actorUserId, String target,
                      String detail, String requestId) {
        this.eventType = eventType;
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.target = target;
        this.detail = detail;
        this.requestId = requestId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getTarget() {
        return target;
    }

    public String getDetail() {
        return detail;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
