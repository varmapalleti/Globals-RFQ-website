package com.globalrfq.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway")
public record GatewaySecurityProperties(
        boolean secretEnabled,
        String sharedSecret,
        String headerName,
        int trustedProxyCount
) {
    public GatewaySecurityProperties {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalStateException("app.gateway.header-name must not be blank");
        }
        if (secretEnabled && (sharedSecret == null || sharedSecret.length() < 32)) {
            throw new IllegalStateException("GATEWAY_SHARED_SECRET must be at least 32 characters when gateway secret forwarding is enabled");
        }
        if (trustedProxyCount < 0) {
            trustedProxyCount = 0;
        }
    }
}
