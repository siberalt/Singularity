package investtech.factory.broker;

import investtech.broker.contract.run.BrokerInterface;
import investtech.configuration.ConfigurationInterface;
import investtech.factory.ServiceContainer;

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
