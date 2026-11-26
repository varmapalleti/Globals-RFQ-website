package com.globalrfq.identity.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.internal-service")
public record InternalServiceProperties(String rfqServiceToken) {
}
