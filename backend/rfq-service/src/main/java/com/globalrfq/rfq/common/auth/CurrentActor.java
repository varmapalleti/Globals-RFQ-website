package com.globalrfq.rfq.common.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentActor {
    private CurrentActor() { }

    public static IdentityPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof IdentityPrincipal principal)) {
            throw new IllegalStateException("Authenticated Identity principal is missing");
        }
        return principal;
    }
}
