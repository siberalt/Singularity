package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.OperationRepository;
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
        OperationRepository operationRepository,
        Clock clock
    ) {
        this(
            candleRepository,
            instrumentRepository,
            orderRepository,
            operationRepository,
            clock,
            MockBroker.DEFAULT_COMMISSION_RATIO,
            MockBroker.DEFAULT_ID
        );
    }

    public EventMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock,
        double commissionRatio,
        String id
    ) {
        super(candleRepository, instrumentRepository, orderRepository, operationRepository, clock, commissionRatio, id);
        Set<String> instrumentIds = instrumentRepository.getAll(id)
            .stream()
            .map(Instrument::getUid)
            .collect(Collectors.toSet());
        this.orderService = new EventSimulatedOrderService(
            this,
            orderRepository,
            operationRepository,
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
        OperationRepository operationRepository,
        Clock clock
    ) {
        return new Builder(
            candleRepository,
            instrumentRepository,
            orderRepository,
            operationRepository,
            clock
        );
    }

    public static class Builder {
        protected String id = "mock-broker";
        protected double commissionRatio = DEFAULT_COMMISSION_RATIO;
        protected final ReadCandleRepository candleRepository;
        protected final ReadInstrumentRepository instrumentRepository;
        protected final OrderRepository orderRepository;
        protected final OperationRepository operationRepository;
        protected final Clock clock;

        public Builder(
            ReadCandleRepository candleRepository,
            ReadInstrumentRepository instrumentRepository,
            OrderRepository orderRepository,
            OperationRepository operationRepository,
            Clock clock
        ) {
            this.candleRepository = candleRepository;
            this.instrumentRepository = instrumentRepository;
            this.orderRepository = orderRepository;
            this.operationRepository = operationRepository;
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
                operationRepository,
                clock,
                commissionRatio,
                id
            );
        }
    }
}
