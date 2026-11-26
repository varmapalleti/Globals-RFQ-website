package com.globalrfq.identity.notification.provider;

public interface EmailSender {
    String providerName();
    boolean isMock();
    void send(EmailMessage message);
}