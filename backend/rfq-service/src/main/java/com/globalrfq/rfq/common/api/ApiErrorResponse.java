package com.globalrfq.rfq.common.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        boolean success,
        int status,
        String errorCode,
        String message,
        String path,
        List<FieldError> fieldErrors,
        Instant timestamp,
        String correlationId
) {
    public record FieldError(String field, String message) { }
}
