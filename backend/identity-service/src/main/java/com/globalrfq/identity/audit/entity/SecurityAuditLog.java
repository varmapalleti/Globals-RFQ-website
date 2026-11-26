package com.globalrfq.identity.audit.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "security_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "service_name", nullable = false, length = 50)
    private String serviceName = "identity-service";

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id", length = 50)
    private String entityId;

    @Column(name = "action", length = 30)
    private String action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "outcome", nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "json")
    private Map<String, Object> metadata;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
