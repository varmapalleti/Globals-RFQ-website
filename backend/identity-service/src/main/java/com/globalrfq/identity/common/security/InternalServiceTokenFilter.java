package com.globalrfq.identity.common.security;

import com.globalrfq.identity.common.config.InternalServiceProperties;
import com.globalrfq.identity.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

public class InternalServiceTokenFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Identity-Service-Token";
    private static final String INTERNAL_PATH_PREFIX = "/internal/identity/";

    private final InternalServiceProperties properties;
    private final SecurityErrorWriter errorWriter;

    public InternalServiceTokenFilter(InternalServiceProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String expected = properties.rfqServiceToken();
        String provided = request.getHeader(HEADER_NAME);
        byte[] expectedBytes = expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided == null ? new byte[0] : provided.getBytes(StandardCharsets.UTF_8);

        if (expectedBytes.length < 32 || !MessageDigest.isEqual(expectedBytes, providedBytes)) {
            errorWriter.write(request, response, ErrorCode.ACCESS_DENIED, "Internal service authentication failed");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                "rfq-service", null, List.of(new SimpleGrantedAuthority("ROLE_RFQ_SERVICE")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
        chain.doFilter(request, response);
    }
}
