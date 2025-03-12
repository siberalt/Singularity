package com.siberalt.singularity.service.factory.broker;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.FactoryInterface;
import com.siberalt.singularity.service.ServiceRegistry;

public interface BrokerFactoryInterface extends FactoryInterface {
    BrokerInterface create(ServiceDetails serviceDetails, DependencyManager dependencyManager);
}
