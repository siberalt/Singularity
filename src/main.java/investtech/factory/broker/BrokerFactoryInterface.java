package investtech.factory.broker;

import investtech.broker.contract.execution.BrokerInterface;
import investtech.configuration.ConfigurationInterface;
import investtech.factory.FactoryInterface;
import investtech.factory.ServiceContainer;

public interface BrokerFactoryInterface extends FactoryInterface {
    BrokerInterface create(ConfigurationInterface config, ServiceContainer serviceManager);
}
