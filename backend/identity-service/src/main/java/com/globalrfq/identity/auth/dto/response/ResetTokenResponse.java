package com.globalrfq.identity.auth.dto.response;

public record ResetTokenResponse(
        String resetToken,
        long expiresInSeconds
) {}