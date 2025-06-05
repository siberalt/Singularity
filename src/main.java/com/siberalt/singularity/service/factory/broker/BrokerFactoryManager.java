package com.siberalt.singularity.service.factory.broker;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;

import java.util.HashMap;

public class BrokerFactoryManager implements BrokerFactory {
    HashMap<String, BrokerFactory> brokerTypes;

    public BrokerFactoryManager registerType(String type, BrokerFactory brokerFactory){
        brokerTypes.put(type, brokerFactory);

        return this;
    }

    @Override
    public Broker create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        String type = (String) serviceDetails.config().get("@type");

        if (!brokerTypes.containsKey(type)) {
            // TODO: throw exception
        }

        return brokerTypes.get(type).create(serviceDetails, dependencyManager);
    }
}
