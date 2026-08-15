package com.siberalt.singularity.broker.impl.tinkoff.execution;

import com.siberalt.singularity.broker.contract.execution.StopOrderServiceAwareBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.StopOrderService;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;
import ru.tinkoff.piapi.contract.v1.StopOrdersServiceGrpc;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

public class TinkoffBroker extends AbstractTinkoffBroker implements StopOrderServiceAwareBroker {
    protected StopOrderServiceInterface stopOrderService;

    public TinkoffBroker(ConnectorConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected void init(ConnectorConfiguration configuration) {
        super.init(configuration);
        stopOrderService = new StopOrderService(StopOrdersServiceGrpc.newBlockingStub(serviceStubFactory.getChannel()));
    }

    @Override
    public StopOrderServiceInterface getStopOrderService() {
        return stopOrderService;
    }
}
