package com.siberalt.singularity.factory.broker;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.factory.ServiceContainer;

import java.util.HashMap;

public class BrokerFactoryManager implements BrokerFactoryInterface{
    HashMap<String, BrokerFactoryInterface> brokerTypes;

    public BrokerFactoryManager registerType(String type, BrokerFactoryInterface strategyFactory){
        brokerTypes.put(type, strategyFactory);

        return this;
    }

    public BrokerInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        String type = (String) config.get("@type");

        if (!brokerTypes.containsKey(type)) {
            // TODO: throw exception
        }

        return brokerTypes.get(type).create(config, serviceManager);
    }
}
