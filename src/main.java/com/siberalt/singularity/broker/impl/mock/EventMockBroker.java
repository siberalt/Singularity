package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
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
        this(
            candleRepository,
            instrumentRepository,
            orderRepository,
            clock,
            MockBroker.DEFAULT_COMMISSION_RATIO,
            MockBroker.DEFAULT_ID
        );
    }

    public EventMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        Clock clock,
        double commissionRatio,
        String id
    ) {
        super(candleRepository, instrumentRepository, orderRepository, clock, commissionRatio, id);
        Set<String> instrumentIds = instrumentRepository.getAll()
            .stream()
            .map(Instrument::getUid)
            .collect(Collectors.toSet());
        this.orderService = new EventSimulatedOrderService(
            this,
            orderRepository,
            new CommissionTransactionSpecProvider(commissionRatio),
            new OrderTransactionSpecProvider()
        );
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

    public static Builder builder(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        Clock clock
    ) {
        return new Builder(
            candleRepository,
            instrumentRepository,
            orderRepository,
            clock
        );
    }

    public static class Builder {
        protected String id = "mock-broker";
        protected double commissionRatio = DEFAULT_COMMISSION_RATIO;
        protected final ReadCandleRepository candleRepository;
        protected final ReadInstrumentRepository instrumentRepository;
        protected final OrderRepository orderRepository;
        protected final Clock clock;

        public Builder(
            ReadCandleRepository candleRepository,
            ReadInstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            Clock clock
        ) {
            this.candleRepository = candleRepository;
            this.instrumentRepository = instrumentRepository;
            this.orderRepository = orderRepository;
            this.clock = clock;
        }

        public Builder setId(String id) {
            this.id = id;
            return this;
        }

        public Builder setCommissionRatio(double commissionRatio) {
            this.commissionRatio = commissionRatio;
            return this;
        }

        public EventMockBroker build() {
            return new EventMockBroker(
                candleRepository,
                instrumentRepository,
                orderRepository,
                clock,
                commissionRatio,
                id
            );
        }
    }
}
