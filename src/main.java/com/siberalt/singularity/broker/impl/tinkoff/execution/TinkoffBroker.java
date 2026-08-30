package com.siberalt.singularity.broker.impl.tinkoff.execution;

import com.siberalt.singularity.broker.contract.execution.StopOrderServiceAwareBroker;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServices;

public class TinkoffBroker extends AbstractTinkoffBroker implements StopOrderServiceAwareBroker {
    private final StopOrderServiceInterface stopOrderService;

    public TinkoffBroker(TinkoffServices services, StopOrderServiceInterface stopOrderService) {
        super(services);
        this.stopOrderService = stopOrderService;
    }

    @Override
    public StopOrderServiceInterface getStopOrderService() {
        return stopOrderService;
    }
}
