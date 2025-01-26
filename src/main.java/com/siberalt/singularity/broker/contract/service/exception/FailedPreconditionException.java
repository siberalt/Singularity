package com.siberalt.singularity.broker.contract.service.exception;

public class FailedPreconditionException extends AbstractException{
    public FailedPreconditionException(ErrorCode errorCode) {
        super(errorCode);
    }

    public FailedPreconditionException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
