package com.siberalt.singularity.service;

public interface Configurable {
    void configure(ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
