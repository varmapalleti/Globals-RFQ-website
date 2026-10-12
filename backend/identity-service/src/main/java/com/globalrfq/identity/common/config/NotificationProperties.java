package com.globalrfq.identity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        String emailProvider,
        String smsProvider,
        String fromEmail,
        String fromName,
        String sesRegion,
        String sesConfigurationSet,
        boolean logSensitiveInMock
) {}