package com.globalrfq.rfq.common.auth;

import java.util.List;

public record IdentityContextResponse(Long userId, String email, List<String> roles) { }
