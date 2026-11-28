package com.globalrfq.rfq.notification.service;

import com.globalrfq.rfq.common.auth.IdentityPrincipal;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.intake.dto.PageResponse;
import com.globalrfq.rfq.notification.dto.NotificationResponse;
import com.globalrfq.rfq.notification.entity.InAppNotification;
import com.globalrfq.rfq.notification.entity.NotificationType;
import com.globalrfq.rfq.notification.repository.InAppNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
public class InAppNotificationService {
    private final InAppNotificationRepository notifications;
    private final RfqWorkflowEmailDispatcher emailDispatcher;

    public InAppNotificationService(InAppNotificationRepository notifications) {
        this(notifications, null);
    }

    @Autowired
    public InAppNotificationService(
            InAppNotificationRepository notifications,
            RfqWorkflowEmailDispatcher emailDispatcher) {
        this.notifications = notifications;
        this.emailDispatcher = emailDispatcher;
    }

    /** Send an in-app notification and queue a corresponding workflow email after commit. */
    @Transactional
    public void send(Long userId, NotificationType type, String title, String message, String rfqPublicId) {
        notifications.save(new InAppNotification(userId, type, title, message, rfqPublicId));
        if (emailDispatcher != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        emailDispatcher.dispatch(userId, type, title, message, rfqPublicId);
                    }
                });
            } else {
                emailDispatcher.dispatch(userId, type, title, message, rfqPublicId);
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listForCurrentUser(IdentityPrincipal actor, int page, int size) {
        var result = notifications.findByUserIdOrderByCreatedAtDesc(actor.userId(), PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(IdentityPrincipal actor) {
        return Map.of("unreadCount", notifications.countByUserIdAndReadFalse(actor.userId()));
    }

    @Transactional
    public NotificationResponse markRead(Long id, IdentityPrincipal actor) {
        InAppNotification notification = notifications.findById(id)
                .orElseThrow(() -> new RfqException(RfqErrorCode.RESOURCE_NOT_FOUND, "Notification was not found"));
        if (!notification.getUserId().equals(actor.userId())) {
            throw new RfqException(RfqErrorCode.ACCESS_DENIED, "You cannot access this notification");
        }
        notification.markRead();
        notifications.save(notification);
        return NotificationResponse.from(notification);
    }

    @Transactional
    public Map<String, Integer> markAllRead(IdentityPrincipal actor) {
        int count = notifications.markAllReadForUser(actor.userId());
        return Map.of("markedRead", count);
    }
}
