package com.globalrfq.rfq.common.auth;

import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.SecurityErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RfqGatewaySecretFilter extends OncePerRequestFilter {
    private final GatewayProperties properties;
    private final SecurityErrorWriter errorWriter;

    public RfqGatewaySecretFilter(GatewayProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
        if (properties.secretEnabled() && (properties.sharedSecret() == null || properties.sharedSecret().length() < 32)) {
            throw new IllegalStateException("GATEWAY_SHARED_SECRET must contain at least 32 characters");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.secretEnabled() || request.getRequestURI().startsWith("/actuator/health")
                || request.getRequestURI().startsWith("/internal/attachment-scanner/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = properties.sharedSecret();
        String provided = request.getHeader(properties.headerName());
        byte[] expectedBytes = expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            errorWriter.write(request, response, RfqErrorCode.ACCESS_DENIED, "Requests must come through the API Gateway");
            return;
        }
        chain.doFilter(request, response);
    }
}
