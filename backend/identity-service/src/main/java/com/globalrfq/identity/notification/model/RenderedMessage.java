package com.globalrfq.identity.notification.model;

public record RenderedMessage(
        String subject,
        String htmlBody,
        String textBody
) {}