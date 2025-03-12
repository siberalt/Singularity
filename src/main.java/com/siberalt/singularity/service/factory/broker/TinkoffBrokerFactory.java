package com.siberalt.singularity.service.factory.broker;

import com.siberalt.singularity.broker.impl.tinkoff.execution.TinkoffBroker;
import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.FactoryInterface;

public class TinkoffBrokerFactory implements FactoryInterface {
    @Override
    public Object create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        return new TinkoffBroker((String) serviceDetails.config().get("token"));
    }
}
