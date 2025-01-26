package com.siberalt.singularity.factory.strategy;

import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.factory.FactoryInterface;
import com.siberalt.singularity.factory.ServiceContainer;
import com.siberalt.singularity.strategy.StrategyInterface;

public interface StrategyFactoryInterface extends FactoryInterface {
    StrategyInterface create(ConfigurationInterface config, ServiceContainer serviceManager);
}
