package com.globalrfq.identity.common.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.logging.CorrelationIdFilter;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)

public record ApiErrorResponse<T>(
        boolean success,
        int status,
        String errorCode,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors,
        Instant timestamp,
        String correlationId
) {

    public record FieldErrorDetail(String field, String message) {}


    public static ApiErrorResponse of (ErrorCode code, String message, String path, List<FieldErrorDetail>fieldErrors){
        return new ApiErrorResponse(false, code.getStatus().value(), code.name(), message, path, fieldErrors, Instant.now(), MDC.get(CorrelationIdFilter.MDC_KEY));
    }


    public static ApiErrorResponse of (ErrorCode code, String message, String path){
        return of (code, message, path, null);
    }


}
