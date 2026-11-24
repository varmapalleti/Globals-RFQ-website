package com.globalrfq.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterTest {

    @Test
    void enforcesBurstCapacityPerClientKey() {
        InMemoryTokenBucketRateLimiter limiter = new InMemoryTokenBucketRateLimiter(1, 3);

        assertTrue(limiter.isAllowed("identity-sensitive-auth", "10.0.0.1").block().isAllowed());
        assertTrue(limiter.isAllowed("identity-sensitive-auth", "10.0.0.1").block().isAllowed());
        assertTrue(limiter.isAllowed("identity-sensitive-auth", "10.0.0.1").block().isAllowed());
        assertFalse(limiter.isAllowed("identity-sensitive-auth", "10.0.0.1").block().isAllowed());

        // A distinct key still has its own bucket
        assertTrue(limiter.isAllowed("identity-sensitive-auth", "10.0.0.2").block().isAllowed());
    }

    @Test
    void resolvesClientIpHonoringTrustedProxyCount() {
        MockServerHttpRequest spoofed = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Forwarded-For", "1.2.3.4")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.50", 12345))
                .build();
        assertEquals("192.168.1.50", RateLimiterConfig.resolveClientIp(spoofed, 0));

        MockServerHttpRequest proxied = MockServerHttpRequest.post("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.9, 10.0.0.2")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.2", 12345))
                .build();
        assertEquals("203.0.113.9", RateLimiterConfig.resolveClientIp(proxied, 2));
        assertEquals("10.0.0.2", RateLimiterConfig.resolveClientIp(proxied, 1));
    }
}
