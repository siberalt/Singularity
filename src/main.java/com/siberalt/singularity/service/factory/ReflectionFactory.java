package com.siberalt.singularity.service.factory;

import com.siberalt.singularity.service.Configurable;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;

public class ReflectionFactory implements Factory {
    @Override
    public Object create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        try {
            Class<?> serviceClass = serviceDetails.serviceClass();
            Object service = serviceClass.getDeclaredConstructor().newInstance();

            if (service instanceof Configurable) {
                ((Configurable) service).configure(serviceDetails, dependencyManager);
            }

            return service;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create service instance", e);
        }
    }
}
