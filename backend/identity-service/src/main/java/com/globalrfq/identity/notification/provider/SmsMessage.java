package com.globalrfq.identity.notification.provider;

public record SmsMessage(
        String to,
        String body
) {}
