package com.globalrfq.identity.notification.entity;

import com.globalrfq.identity.notification.model.NotificationChannel;
import com.globalrfq.identity.notification.model.NotificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "notification_outbox")
@Getter
@Setter
@NoArgsConstructor
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "channel", nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "template_code", nullable = false, length = 60)
    private String templateCode;

    @Column(name = "recipient", nullable = false, length = 190)
    private String recipient;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "variables_json", columnDefinition = "json")
    private Map<String, Object> variables;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}