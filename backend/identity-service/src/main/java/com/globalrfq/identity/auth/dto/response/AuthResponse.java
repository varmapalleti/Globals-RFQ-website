package com.globalrfq.identity.auth.dto.response;

import com.globalrfq.identity.user.dto.UserSummary;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserSummary user
) {}