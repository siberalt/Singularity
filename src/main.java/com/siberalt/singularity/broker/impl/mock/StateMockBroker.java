package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

public class StateMockBroker extends MockBroker {
    protected StateMockOrderService orderService;

    public StateMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        Clock clock
    ) {
        super(candleRepository, instrumentStorage, orderRepository, clock);
        this.orderService = new StateMockOrderService(this, orderRepository);
    }

    @Override
    public StateMockOrderService getOrderService() {
        return orderService;
    }
}
