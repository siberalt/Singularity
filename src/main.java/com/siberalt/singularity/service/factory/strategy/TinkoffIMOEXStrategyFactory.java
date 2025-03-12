package com.siberalt.singularity.service.factory.strategy;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.impl.TinkoffIMOEXStrategy;

public class TinkoffIMOEXStrategyFactory implements StrategyFactoryInterface {
    @Override
    public StrategyInterface create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        return new TinkoffIMOEXStrategy();
    }
}
