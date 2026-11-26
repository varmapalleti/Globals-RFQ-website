package com.globalrfq.rfq.common.error;

public class RfqException extends RuntimeException {
    private final RfqErrorCode code;

    public RfqException(RfqErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RfqErrorCode code() {
        return code;
    }
}
