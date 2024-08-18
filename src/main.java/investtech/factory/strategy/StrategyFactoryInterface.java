package investtech.factory.strategy;

import investtech.configuration.ConfigurationInterface;
import investtech.factory.FactoryInterface;
import investtech.factory.ServiceContainer;
import investtech.strategy.StrategyInterface;

public interface StrategyFactoryInterface extends FactoryInterface {
    StrategyInterface create(ConfigurationInterface config, ServiceContainer serviceManager);
}
