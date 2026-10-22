package com.globalrfq.gateway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GatewayRequestLoggingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRequestLoggingFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final Pattern SENSITIVE_PATH_SEGMENT = Pattern.compile(
            "(?i)^(?:[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}|"
                    + "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}|"
                    + "[a-z0-9_-]{32,})$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.nanoTime();
        String method = exchange.getRequest().getMethod().name();
        String path = sanitizePath(exchange.getRequest().getURI().getRawPath());
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

        return chain.filter(exchange)
                .doFinally(signal -> log.info(
                        "gateway_request method={} path={} status={} duration_ms={} correlation_id={} signal={}",
                        method,
                        path,
                        responseStatus(exchange),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                        correlationId == null ? "-" : correlationId,
                        signal));
    }

    private static String responseStatus(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        return status == null ? "unset" : Integer.toString(status.value());
    }

    private static String sanitizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return "/";
        }

        String[] segments = rawPath.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            if (SENSITIVE_PATH_SEGMENT.matcher(segments[index]).matches()) {
                segments[index] = "{redacted}";
            }
        }
        return String.join("/", segments);
    }
}
