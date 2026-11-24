package com.globalrfq.gateway.security;

import com.globalrfq.gateway.config.GatewaySecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    @Bean
    public InMemoryTokenBucketRateLimiter inMemoryTokenBucketRateLimiter(
            ConfigurationService configurationService,
            @Value("${app.gateway.auth-rate-limit.replenish-rate:10}") int replenishRate,
            @Value("${app.gateway.auth-rate-limit.burst-capacity:20}") int burstCapacity) {
        return new InMemoryTokenBucketRateLimiter(configurationService, replenishRate, burstCapacity);
    }

    @Bean
    public KeyResolver clientIpKeyResolver(GatewaySecurityProperties properties) {
        int trustedProxyCount = properties.trustedProxyCount();
        return exchange -> Mono.justOrEmpty(resolveClientIp(exchange.getRequest(), trustedProxyCount));
    }

    static String resolveClientIp(ServerHttpRequest request, int trustedProxyCount) {
        if (trustedProxyCount > 0) {
            String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String[] hops = forwardedFor.split(",");
                int index = Math.max(0, hops.length - trustedProxyCount);
                String candidate = hops[index].trim();
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null) {
            return null;
        }
        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return remoteAddress.getHostString();
    }
}
