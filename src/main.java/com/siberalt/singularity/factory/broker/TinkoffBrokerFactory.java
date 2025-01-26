package com.siberalt.singularity.factory.broker;

import com.siberalt.singularity.broker.contract.execution.BrokerInterface;
import com.siberalt.singularity.broker.impl.tinkoff.execution.TinkoffBroker;
import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.configuration.validation.ConfigValidationAwareInterface;
import com.siberalt.singularity.configuration.validation.ConstraintsAggregate;
import com.siberalt.singularity.configuration.validation.ValueType;
import com.siberalt.singularity.factory.ServiceContainer;

public class TinkoffBrokerFactory implements BrokerFactoryInterface, ConfigValidationAwareInterface {
    public BrokerInterface create(ConfigurationInterface config, ServiceContainer serviceManager) {
        return new TinkoffBroker((String) config.get("token"));
    }

    @Override
    public void fillInConstraints(ConstraintsAggregate constraintsAggregate) {
        constraintsAggregate.add("token", ConstraintsAggregate.of(true, ValueType.STRING));
    }
}
