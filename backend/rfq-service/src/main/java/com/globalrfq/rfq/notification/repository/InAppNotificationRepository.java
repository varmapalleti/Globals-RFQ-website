package com.globalrfq.rfq.notification.repository;

import com.globalrfq.rfq.notification.entity.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    Page<InAppNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndReadFalse(Long userId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.read = false")
    int markAllReadForUser(Long userId);
}
