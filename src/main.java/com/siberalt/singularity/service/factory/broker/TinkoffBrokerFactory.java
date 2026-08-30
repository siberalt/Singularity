package com.siberalt.singularity.service.factory.broker;

import com.siberalt.singularity.broker.impl.tinkoff.execution.TinkoffExecutionBrokerFactory;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.Factory;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

import java.util.Properties;

public class TinkoffBrokerFactory implements Factory {
    @Override
    public Object create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        var properties = new Properties();
        properties.put("token", serviceDetails.config().get("token"));

        return TinkoffExecutionBrokerFactory.create(ConnectorConfiguration.loadFromProperties(properties));
    }
}
