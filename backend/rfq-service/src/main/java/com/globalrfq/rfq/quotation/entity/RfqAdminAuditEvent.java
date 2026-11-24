package com.globalrfq.rfq.quotation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "rfq_admin_audit_events")
public class RfqAdminAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_type", nullable = false, length = 80)
    private String actionType;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "json")
    private String afterJson;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    protected RfqAdminAuditEvent() { }

    public RfqAdminAuditEvent(String actionType, Long actorUserId, String reason,
                              String beforeJson, String afterJson, String correlationId) {
        this.actionType = actionType;
        this.actorUserId = actorUserId;
        this.reason = reason;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public String getActionType() { return actionType; }
    public Long getActorUserId() { return actorUserId; }
    public String getReason() { return reason; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
}
