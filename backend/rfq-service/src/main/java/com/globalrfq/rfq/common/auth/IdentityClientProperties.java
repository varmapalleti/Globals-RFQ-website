package com.globalrfq.rfq.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.identity")
public record IdentityClientProperties(String internalServiceToken, String serviceName) { }
