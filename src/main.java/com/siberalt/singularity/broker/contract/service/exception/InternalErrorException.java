package com.siberalt.singularity.broker.contract.service.exception;

public class InternalErrorException extends AbstractException{
    public InternalErrorException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InternalErrorException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
