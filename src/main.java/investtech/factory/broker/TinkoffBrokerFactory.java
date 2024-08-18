package investtech.factory.broker;

import investtech.broker.contract.run.BrokerInterface;
import investtech.broker.impl.tinkoff.run.TinkoffBroker;
import investtech.configuration.ConfigurationInterface;
import investtech.configuration.validation.ConfigValidationAwareInterface;
import investtech.configuration.validation.ConstraintsAggregate;
import investtech.configuration.validation.ValueType;
import investtech.factory.ServiceContainer;

public class TinkoffBrokerFactory implements BrokerFactoryInterface, ConfigValidationAwareInterface {
    public BrokerInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        return new TinkoffBroker((String) config.get("token"));
    }

    @Override
    public void fillInConstraints(ConstraintsAggregate constraintsAggregate) {
        constraintsAggregate.add("token", ConstraintsAggregate.of(true, ValueType.STRING));
    }
}
