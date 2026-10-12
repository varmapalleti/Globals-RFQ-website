package com.globalrfq.identity.notification.repository;

import com.globalrfq.identity.notification.entity.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {}