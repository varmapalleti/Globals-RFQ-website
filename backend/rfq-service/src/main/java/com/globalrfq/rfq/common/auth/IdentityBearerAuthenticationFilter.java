package com.globalrfq.rfq.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.SecurityErrorWriter;

import java.io.IOException;

public class IdentityBearerAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private final IdentityAuthorizationClient identityClient;
    private final SecurityErrorWriter errorWriter;

    public IdentityBearerAuthenticationFilter(IdentityAuthorizationClient identityClient, SecurityErrorWriter errorWriter) {
        this.identityClient = identityClient;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health")
                || request.getRequestURI().startsWith("/internal/attachment-scanner/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            errorWriter.write(request, response, RfqErrorCode.UNAUTHORIZED, "Authentication is required");
            return;
        }

        try {
            IdentityPrincipal identity = identityClient.validate(token);
            var authorities = identity.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(identity, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (InvalidIdentityTokenException exception) {
            errorWriter.write(request, response, RfqErrorCode.UNAUTHORIZED, "Access token is invalid or expired");
        } catch (IdentityUnavailableException exception) {
            errorWriter.write(request, response, RfqErrorCode.IDENTITY_UNAVAILABLE, "Identity authorization is temporarily unavailable");
        }
    }
}
