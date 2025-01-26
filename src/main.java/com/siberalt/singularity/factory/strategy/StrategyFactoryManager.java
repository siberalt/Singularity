package com.siberalt.singularity.factory.strategy;

import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.factory.ServiceContainer;
import com.siberalt.singularity.strategy.StrategyInterface;

import java.util.HashMap;

public class StrategyFactoryManager implements StrategyFactoryInterface {
    HashMap<String, StrategyFactoryInterface> strategyTypes;

    public StrategyFactoryManager registerType(String type, StrategyFactoryInterface strategyFactory){
        strategyTypes.put(type, strategyFactory);

        return this;
    }

    public StrategyInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        String type = (String) config.get("@type");

        if (!strategyTypes.containsKey(type)) {
            // TODO: throw exception
        }

        return strategyTypes.get(type).create(config, serviceManager);
    }
}
