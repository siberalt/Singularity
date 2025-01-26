package com.siberalt.singularity.broker.contract.service.exception;

public class UnimplementedException extends AbstractException {
    public UnimplementedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public UnimplementedException() {
        super(ErrorCode.UNIMPLEMENTED);
    }
}
