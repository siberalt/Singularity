package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.order.OrderRepository;

public class EventMockBroker extends MockBroker {
    protected EventSimulatedOrderService orderService;

    public EventMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository
    ) {
        super(candleRepository, instrumentStorage, orderRepository);
        this.orderService = new EventSimulatedOrderService(this, orderRepository);
    }

    @Override
    public EventSimulatedOrderService getOrderService() {
        return orderService;
    }
}
