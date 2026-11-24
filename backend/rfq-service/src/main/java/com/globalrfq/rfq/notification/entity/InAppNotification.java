package com.globalrfq.rfq.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "in_app_notifications")
public class InAppNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "rfq_public_id", length = 36)
    private String rfqPublicId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected InAppNotification() { }

    public InAppNotification(Long userId, NotificationType type, String title, String message,
                             String rfqPublicId) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.rfqPublicId = rfqPublicId;
    }

    public void markRead() {
        this.read = true;
        this.readAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getRfqPublicId() { return rfqPublicId; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
