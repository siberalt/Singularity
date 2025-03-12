package com.siberalt.singularity.service.factory.strategy;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.FactoryInterface;
import com.siberalt.singularity.strategy.StrategyInterface;

public interface StrategyFactoryInterface extends FactoryInterface {
    StrategyInterface create(ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
