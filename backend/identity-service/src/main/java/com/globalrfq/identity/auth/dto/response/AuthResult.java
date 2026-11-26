package com.globalrfq.identity.auth.dto.response;

public record AuthResult(
        AuthResponse response,
        String refreshToken,
        long refreshMaxAgeSeconds
) {}
