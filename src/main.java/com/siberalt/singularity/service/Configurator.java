package com.siberalt.singularity.service;

public interface Configurator {
    void configure(Object service, ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
