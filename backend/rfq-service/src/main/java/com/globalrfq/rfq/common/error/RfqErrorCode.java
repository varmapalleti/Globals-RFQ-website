package com.globalrfq.rfq.common.error;

import org.springframework.http.HttpStatus;

public enum RfqErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    INVALID_WORKFLOW_TRANSITION(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    IDENTITY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    RfqErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
