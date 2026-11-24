package com.globalrfq.rfq.common.auth;

public record UserEligibilityResponse(Long userId, String requiredRole, boolean eligible) { }
