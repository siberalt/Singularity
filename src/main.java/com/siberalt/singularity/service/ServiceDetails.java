package com.siberalt.singularity.service;

import com.siberalt.singularity.configuration.ConfigInterface;

public record ServiceDetails(String serviceId, Class<?> serviceClass, ConfigInterface config) {
    public ServiceDetails(String serviceId){
        this(serviceId, null, null);
    }

    public ServiceDetails updateConfig(ConfigInterface config) {
        return new ServiceDetails(serviceId, serviceClass, config);
    }

    public ServiceDetails updateServiceId(String serviceId) {
        return new ServiceDetails(serviceId, serviceClass, config);
    }

    public ServiceDetails updateServiceClass(Class<?> serviceClass) {
        return new ServiceDetails(serviceId, serviceClass, config);
    }
}
