package com.siberalt.singularity.broker.contract.service.exception;

public enum ErrorType {
    UNIMPLEMENTED(12001),
    UNAVAILABLE(12002),
    UNKNOWN(12003),
    INVALID_REQUEST(30000),
    PERMISSION_DENIED(40000),
    NOT_FOUND(50000),
    INTERNAL_ERROR(70000),
    RESOURCE_EXHAUSTED(80000),
    FAILED_PRECONDITION(90000);

    private final int shift;

    private int codeSequence;

    ErrorType(int shift) {
        this.shift = shift;
        this.codeSequence = shift;
    }

    public int getNextCode(){
        return ++codeSequence;
    }

    public int getLastCode() {
        return codeSequence;
    }

    public int getShift() {
        return shift;
    }
}
