package com.siberalt.singularity.broker.impl.tinkoff.execution;

import com.siberalt.singularity.broker.impl.tinkoff.shared.StopOrderService;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServices;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServicesFactory;
import ru.tinkoff.piapi.contract.v1.StopOrdersServiceGrpc;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

public class TinkoffExecutionBrokerFactory {
    public static TinkoffBroker create(ConnectorConfiguration configuration) {
        return create(configuration, new TinkoffServicesFactory());
    }

    public static TinkoffBroker create(ConnectorConfiguration configuration, TinkoffServicesFactory servicesFactory) {
        TinkoffServices services = servicesFactory.create(configuration);
        StopOrderService stopOrderService = new StopOrderService(
            StopOrdersServiceGrpc.newBlockingStub(services.serviceStubFactory().getChannel())
        );

        return new TinkoffBroker(services, stopOrderService);
    }
}
