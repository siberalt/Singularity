package com.siberalt.singularity.broker.contract.service.exception;

public class PermissionDeniedException extends AbstractException{
    public PermissionDeniedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PermissionDeniedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
