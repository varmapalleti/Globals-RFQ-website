package com.globalrfq.gateway.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class GatewayFallbackController {

    @RequestMapping("/fallback/identity")
    public ResponseEntity<GatewayError> identityUnavailable(ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
        GatewayError body = new GatewayError(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "SERVICE_UNAVAILABLE",
                "The identity service is temporarily unavailable. Please retry shortly.",
                request.getPath().value(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(body);
    }

    public record GatewayError(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            String correlationId
    ) {
    }
}
