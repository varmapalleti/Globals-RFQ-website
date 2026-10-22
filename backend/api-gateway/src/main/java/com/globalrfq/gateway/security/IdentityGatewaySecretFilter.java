package com.globalrfq.gateway.security;

import com.globalrfq.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class IdentityGatewaySecretFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties properties;

    public IdentityGatewaySecretFilter(GatewaySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || !route.getId().startsWith("identity-")) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    // Never forward a value supplied by the public caller.
                    headers.remove(properties.headerName());
                    if (properties.secretEnabled()) {
                        headers.set(properties.headerName(), properties.sharedSecret());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
