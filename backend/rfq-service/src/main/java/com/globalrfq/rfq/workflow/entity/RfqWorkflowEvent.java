package com.globalrfq.rfq.workflow.entity;

import com.globalrfq.rfq.intake.entity.Rfq;
import com.globalrfq.rfq.intake.entity.RfqStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "rfq_workflow_events")
public class RfqWorkflowEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rfq_id", nullable = false)
    private Rfq rfq;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actor_roles_json", nullable = false, columnDefinition = "json")
    private String actorRolesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 40)
    private RfqStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 40)
    private RfqStatus toStatus;

    @Column(length = 1000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    protected RfqWorkflowEvent() { }

    public RfqWorkflowEvent(Rfq rfq, String eventType, Long actorUserId, String actorRolesJson,
                            RfqStatus fromStatus, RfqStatus toStatus, String reason,
                            String payloadJson, String correlationId) {
        this.rfq = rfq;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.actorRolesJson = actorRolesJson;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.payloadJson = payloadJson;
        this.correlationId = correlationId;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public Long getActorUserId() { return actorUserId; }
    public String getActorRolesJson() { return actorRolesJson; }
    public RfqStatus getFromStatus() { return fromStatus; }
    public RfqStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public String getPayloadJson() { return payloadJson; }
    public String getCorrelationId() { return correlationId; }
    public Instant getOccurredAt() { return occurredAt; }
}
