package com.siberalt.singularity.broker.impl.tinkoff.execution;

import com.siberalt.singularity.broker.contract.execution.StopOrderServiceAwareBrokerInterface;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.StopOrderService;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;

public class TinkoffBroker extends AbstractTinkoffBroker implements StopOrderServiceAwareBrokerInterface {
    protected StopOrderServiceInterface stopOrderService;

    public TinkoffBroker(String token) {
        super(token);
    }

    @Override
    protected void init(String token) {
        super.init(token);
        stopOrderService = new StopOrderService(api.getStopOrdersService());
    }

    @Override
    public StopOrderServiceInterface getStopOrderService() {
        return stopOrderService;
    }
}
