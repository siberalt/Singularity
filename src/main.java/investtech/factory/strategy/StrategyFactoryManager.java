package investtech.factory.strategy;

import investtech.configuration.ConfigurationInterface;
import investtech.factory.ServiceContainer;
import investtech.strategy.StrategyInterface;

import java.util.HashMap;

public class StrategyFactoryManager implements StrategyFactoryInterface {
    HashMap<String, StrategyFactoryInterface> strategyTypes;

    public StrategyFactoryManager registerType(String type, StrategyFactoryInterface strategyFactory){
        strategyTypes.put(type, strategyFactory);

        return this;
    }

    public StrategyInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        String type = (String) config.get("@type");

        if (!strategyTypes.containsKey(type)) {
            // TODO: throw exception
        }

        return strategyTypes.get(type).create(config, serviceManager);
    }
}
