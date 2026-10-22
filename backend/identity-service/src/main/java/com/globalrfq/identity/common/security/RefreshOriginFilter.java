package com.globalrfq.identity.common.security;

import com.globalrfq.identity.common.config.CorsProperties;
import com.globalrfq.identity.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Protects cookie-authenticated refresh/logout endpoints from cross-origin request forgery. */
public class RefreshOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;
    private final SecurityErrorWriter writer;

    public RefreshOriginFilter(CorsProperties cors, SecurityErrorWriter writer) {
        this.allowedOrigins = Set.copyOf(cors.allowedOrigins());
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !("/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            writer.write(request, response, ErrorCode.ACCESS_DENIED, "Request origin is not allowed");
            return;
        }
        chain.doFilter(request, response);
    }
}
