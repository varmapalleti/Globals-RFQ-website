package com.globalrfq.rfq.common.auth;

import java.util.Set;

public record IdentityPrincipal(Long userId, String email, Set<String> roles) { }
