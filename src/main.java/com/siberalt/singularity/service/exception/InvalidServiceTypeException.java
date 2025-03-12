package com.siberalt.singularity.service.exception;

public class InvalidServiceTypeException extends RuntimeException {
    private final String serviceId;
    private final Class<?> expectedType;
    private final Class<?> actualType;

    public InvalidServiceTypeException(String serviceId, Class<?> expectedType, Class<?> actualType) {
        super("Service " + serviceId + " expected to be of type " + expectedType + " but was " + actualType);
        this.serviceId = serviceId;
        this.expectedType = expectedType;
        this.actualType = actualType;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Class<?> getExpectedType() {
        return expectedType;
    }

    public Class<?> getActualType() {
        return actualType;
    }
}
