package com.siberalt.singularity.factory.strategy;

import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.factory.ServiceContainer;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.impl.TinkoffIMOEXStrategy;

public class TinkoffIMOEXStrategyFactory implements StrategyFactoryInterface {
    public StrategyInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        return new TinkoffIMOEXStrategy();
    }
}
