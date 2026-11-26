package com.globalrfq.identity.notification.provider;

public record EmailMessage(
        String from,
        String to,
        String subject,
        String htmlBody,
        String textBody
) {}