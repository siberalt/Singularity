package com.siberalt.singularity.service.factory.broker;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.Factory;

public interface BrokerFactory extends Factory {
    Broker create(ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
