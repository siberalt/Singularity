package com.siberalt.singularity.factory.broker;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.factory.FactoryInterface;
import com.siberalt.singularity.factory.ServiceContainer;

public interface BrokerFactoryInterface extends FactoryInterface {
    BrokerInterface create(ConfigurationInterface config, ServiceContainer serviceManager);
}
