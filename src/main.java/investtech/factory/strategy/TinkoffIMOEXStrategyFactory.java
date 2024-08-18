package investtech.factory.strategy;

import investtech.configuration.ConfigurationInterface;
import investtech.factory.ServiceContainer;
import investtech.strategy.StrategyInterface;
import investtech.strategy.impl.TinkoffIMOEXStrategy;

public class TinkoffIMOEXStrategyFactory implements StrategyFactoryInterface {
    public StrategyInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        return new TinkoffIMOEXStrategy();
    }
}
