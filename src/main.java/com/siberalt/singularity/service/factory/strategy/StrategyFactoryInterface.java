package com.siberalt.singularity.service.factory.strategy;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.Factory;
import com.siberalt.singularity.strategy.Strategy;

public interface StrategyFactoryInterface extends Factory {
    Strategy create(ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
