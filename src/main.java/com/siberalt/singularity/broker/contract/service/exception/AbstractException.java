package com.siberalt.singularity.broker.contract.service.exception;

import java.util.Optional;

public abstract class AbstractException extends Exception {
    protected ErrorCode errorCode;

    public AbstractException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessagePattern());
    }

    public AbstractException(ErrorCode errorCode, String message) {
        super(Optional.of(message).orElse(errorCode.getMessagePattern()));
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
