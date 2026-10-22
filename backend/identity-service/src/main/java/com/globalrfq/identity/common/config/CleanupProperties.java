package com.globalrfq.identity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cleanup")
public record CleanupProperties(int auditRetentionDays) {}
