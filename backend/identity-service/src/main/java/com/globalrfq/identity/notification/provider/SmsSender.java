package com.globalrfq.identity.notification.provider;

public interface SmsSender {
    String providerName();
    boolean isMock();
    void send(SmsMessage message);
}