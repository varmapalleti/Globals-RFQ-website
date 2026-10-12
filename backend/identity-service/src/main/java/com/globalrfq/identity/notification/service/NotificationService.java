package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.notification.model.NotificationRequest;

public interface NotificationService {
    void send(NotificationRequest request);
}