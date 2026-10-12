package com.globalrfq.identity.notification.model;

import java.util.Map;
import java.util.Set;

public record NotificationRequest(
        NotificationChannel channel,
        NotificationTemplate template,
        String recipient,
        Map<String, String> variables,
        Set<String> sensitiveKeys
) {
    public static NotificationRequest email(NotificationTemplate template, String recipient,
                                            Map<String, String> variables, String... sensitiveKeys) {
        return new NotificationRequest(NotificationChannel.EMAIL, template, recipient,
                variables, Set.of(sensitiveKeys));
    }
}