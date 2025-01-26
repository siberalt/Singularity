package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.simulation.shared.instrument.InstrumentStorageInterface;
import com.siberalt.singularity.simulation.shared.market.candle.CandleStorageInterface;

public class StateMockBroker extends MockBroker {
    protected StateMockOrderService orderService;

    public StateMockBroker(CandleStorageInterface candleRepository, InstrumentStorageInterface instrumentStorage) {
        super(candleRepository, instrumentStorage);
        this.orderService = new StateMockOrderService(this);
    }

    @Override
    public StateMockOrderService getOrderService() {
        return orderService;
    }
}
