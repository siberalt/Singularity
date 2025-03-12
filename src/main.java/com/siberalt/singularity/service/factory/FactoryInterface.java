package com.siberalt.singularity.service.factory;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;

public interface FactoryInterface {
    Object create(ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
