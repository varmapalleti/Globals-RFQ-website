package com.globalrfq.rfq.notification.dto;

import com.globalrfq.rfq.notification.entity.InAppNotification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        String rfqPublicId,
        boolean read,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(InAppNotification n) {
        return new NotificationResponse(
                n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                n.getRfqPublicId(), n.isRead(), n.getCreatedAt(), n.getReadAt()
        );
    }
}
