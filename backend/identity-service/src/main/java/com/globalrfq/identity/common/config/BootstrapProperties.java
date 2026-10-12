package com.globalrfq.identity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        String superAdminEmail,
        String superAdminPassword,
        String superAdminName
) {}



