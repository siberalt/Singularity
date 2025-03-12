package com.siberalt.singularity.service.exception;

import java.util.List;

public class ServiceDependencyException extends Exception {
    String serviceId;
    List<String> dependentServicesIds;

    public ServiceDependencyException(String serviceId, List<String> dependentServicesIds) {
        super("Service '" + serviceId + "' is a dependence for services: " + dependentServicesIds);
        this.serviceId = serviceId;
        this.dependentServicesIds = dependentServicesIds;
    }

    public String getServiceId() {
        return serviceId;
    }

    public List<String> getDependentServicesIds() {
        return dependentServicesIds;
    }
}
