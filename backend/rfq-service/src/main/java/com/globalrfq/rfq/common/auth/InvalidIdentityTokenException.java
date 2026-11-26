package com.globalrfq.rfq.common.auth;

public class InvalidIdentityTokenException extends RuntimeException {
    public InvalidIdentityTokenException() {
        super("Access token is invalid or expired");
    }
}
