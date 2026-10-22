package com.globalrfq.identity.notification.repository;

import com.globalrfq.identity.notification.entity.NotificationOutbox;
import com.globalrfq.identity.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {
    @Modifying
    @Query("update NotificationOutbox row set row.status = com.globalrfq.identity.notification.model.NotificationStatus.PROCESSING, row.scheduledAt = :leaseUntil, row.attemptCount = row.attemptCount + 1 "
            + "where row.id = :id and row.payloadEncrypted = true and row.attemptCount < 5 and ((row.status in "
            + "(com.globalrfq.identity.notification.model.NotificationStatus.PENDING, com.globalrfq.identity.notification.model.NotificationStatus.FAILED) "
            + "and (row.scheduledAt is null or row.scheduledAt <= :now)) or (row.status = "
            + "com.globalrfq.identity.notification.model.NotificationStatus.PROCESSING and row.scheduledAt <= :now))")
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Modifying
    @Query("update NotificationOutbox row set row.status = com.globalrfq.identity.notification.model.NotificationStatus.FAILED, "
            + "row.scheduledAt = null, row.failureReason = 'Delivery stopped after maximum attempts' "
            + "where row.id = :id and row.status = com.globalrfq.identity.notification.model.NotificationStatus.PROCESSING "
            + "and row.scheduledAt <= :now and row.attemptCount >= 5")
    int failExhausted(@Param("id") Long id, @Param("now") Instant now);

    List<NotificationOutbox> findTop50ByPayloadEncryptedTrueAndStatusAndScheduledAtIsNullOrderByCreatedAtAsc(NotificationStatus status);
    List<NotificationOutbox> findTop50ByPayloadEncryptedTrueAndStatusAndScheduledAtLessThanEqualAndAttemptCountLessThanOrderByScheduledAtAsc(
            NotificationStatus status, Instant now, int maxAttempts);
    List<NotificationOutbox> findTop50ByPayloadEncryptedTrueAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            NotificationStatus status, Instant now);
}
