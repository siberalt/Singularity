package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.simulation.shared.instrument.InstrumentStorageInterface;
import com.siberalt.singularity.simulation.shared.market.candle.CandleStorageInterface;

public class EventMockBroker extends MockBroker {
    protected EventSimulatedOrderService orderService;

    public EventMockBroker(CandleStorageInterface candleRepository, InstrumentStorageInterface instrumentStorage) {
        super(candleRepository, instrumentStorage);
        this.orderService = new EventSimulatedOrderService(this);
    }

    @Override
    public EventSimulatedOrderService getOrderService() {
        return orderService;
    }
}
