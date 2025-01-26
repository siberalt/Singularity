package com.siberalt.singularity.factory;

import com.siberalt.singularity.configuration.ConfigurationInterface;

public interface FactoryInterface {
    Object create(ConfigurationInterface config, ServiceContainer serviceManager);
}
