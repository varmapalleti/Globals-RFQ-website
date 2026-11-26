package com.globalrfq.rfq.common.api;

import java.time.Instant;

public record ApiResponse<T>(boolean success, String message, T data, Instant timestamp, String correlationId) {
    public static <T> ApiResponse<T> success(String message, T data, String correlationId) {
        return new ApiResponse<>(true, message, data, Instant.now(), correlationId);
    }
}
