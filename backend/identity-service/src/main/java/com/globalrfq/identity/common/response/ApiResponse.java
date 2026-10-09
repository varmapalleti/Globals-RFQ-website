package com.globalrfq.identity.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.globalrfq.identity.common.logging.CorrelationIdFilter;
import org.slf4j.MDC;
import java.time.Instant;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse <T>(
        boolean success,
        String message,
        T data,
        Instant timestamp,
        String correlationId
) {

    public static <T> ApiResponse <T> success(String message, T data){
        return new ApiResponse<>(true, message, data, Instant.now(), MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    public static <T> ApiResponse<T> success(String message){
        return success(message, null);
    }

}
