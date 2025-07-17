package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.Set;
import java.util.stream.Collectors;

public class EventMockBroker extends MockBroker implements EventSubscriptionBroker {
    private final EventSimulatedOrderService orderService;
    private final NewCandleSubscriptionManager subscriptionManager;

    public EventMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        Clock clock
    ) {
        super(candleRepository, instrumentRepository, orderRepository, clock);
        Set<String> instrumentIds = instrumentRepository.getAll()
            .stream()
            .map(Instrument::getUid)
            .collect(Collectors.toSet());
        this.orderService = new EventSimulatedOrderService(this, orderRepository);
        this.subscriptionManager = new NewCandleSubscriptionManager(candleRepository, instrumentIds);
    }

    @Override
    public EventSimulatedOrderService getOrderService() {
        return orderService;
    }

    @Override
    public NewCandleSubscriptionManager getSubscriptionManager() {
        return subscriptionManager;
    }
}
