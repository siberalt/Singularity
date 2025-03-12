package com.siberalt.singularity.service.exception;

public class ServiceNotFoundException extends RuntimeException {
    String serviceId;

    public ServiceNotFoundException(String serviceId) {
        super("Service " + serviceId + " not found");
        this.serviceId = serviceId;
    }

    public String getServiceId() {
        return serviceId;
    }
}
