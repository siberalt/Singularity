package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.order.OrderRepository;

public class StateMockBroker extends MockBroker {
    protected StateMockOrderService orderService;

    public StateMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository
    ) {
        super(candleRepository, instrumentStorage, orderRepository);
        this.orderService = new StateMockOrderService(this, orderRepository);
    }

    @Override
    public StateMockOrderService getOrderService() {
        return orderService;
    }
}
