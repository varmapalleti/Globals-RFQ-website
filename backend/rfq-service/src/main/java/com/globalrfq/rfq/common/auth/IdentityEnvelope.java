package com.globalrfq.rfq.common.auth;

import java.time.Instant;

public record IdentityEnvelope<T>(boolean success, String message, T data, Instant timestamp, String correlationId) { }
