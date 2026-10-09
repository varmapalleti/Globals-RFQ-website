package com.globalrfq.identity.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status){
        this.status=status;
    }

    public HttpStatus getStatus(){
        return status;
    }


}
