package com.siberalt.singularity.service.factory.strategy;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.strategy.Strategy;

import java.util.HashMap;

public class StrategyFactoryManager implements StrategyFactoryInterface {
    HashMap<String, StrategyFactoryInterface> strategyTypes;

    public StrategyFactoryManager registerType(String type, StrategyFactoryInterface strategyFactory){
        strategyTypes.put(type, strategyFactory);

        return this;
    }

    public Strategy create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        String type = (String) serviceDetails.config().get("@type");

        if (!strategyTypes.containsKey(type)) {
            // TODO: throw exception
        }

        return strategyTypes.get(type).create(serviceDetails, dependencyManager);
    }
}
