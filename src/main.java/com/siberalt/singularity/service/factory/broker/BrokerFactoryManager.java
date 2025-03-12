package com.siberalt.singularity.service.factory.broker;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.ServiceRegistry;
import com.siberalt.singularity.service.factory.FactoryInterface;

import java.util.HashMap;

public class BrokerFactoryManager implements BrokerFactoryInterface {
    HashMap<String, BrokerFactoryInterface> brokerTypes;

    public BrokerFactoryManager registerType(String type, BrokerFactoryInterface brokerFactory){
        brokerTypes.put(type, brokerFactory);

        return this;
    }

    @Override
    public BrokerInterface create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        String type = (String) serviceDetails.config().get("@type");

        if (!brokerTypes.containsKey(type)) {
            // TODO: throw exception
        }

        return brokerTypes.get(type).create(serviceDetails, dependencyManager);
    }
}
