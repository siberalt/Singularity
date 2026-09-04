package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.impl.mock.factory.DefaultEventOrderServiceFactory;
import com.siberalt.singularity.broker.impl.mock.factory.MockServicesFactory;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.stream.Collectors;

public class EventMockBroker extends MockBroker implements EventSubscriptionBroker {
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
        this(
            candleRepository,
            instrumentRepository,
            orderRepository,
            operationRepository,
            clock,
            commissionRatio,
            id,
            new DefaultEventOrderServiceFactory()
        );
    }

    /**
     * Lets the caller plug in its own {@link DefaultEventOrderServiceFactory} - e.g. one built with
     * custom commission/order transaction spec providers - instead of the defaulted one the other
     * constructors use. Kept specific to {@link DefaultEventOrderServiceFactory} (rather than accepting
     * any {@link com.siberalt.singularity.broker.impl.mock.factory.OrderServiceFactory}) because
     * {@link #getPendingOrderHandler()} relies on that factory's pending-order handler.
     */
    public EventMockBroker(
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock,
        double commissionRatio,
        String id,
        DefaultEventOrderServiceFactory orderServiceFactory
    ) {
        super(
            candleRepository,
            instrumentRepository,
            orderRepository,
            operationRepository,
            clock,
            commissionRatio,
            id,
            new MockServicesFactory().orderServiceFactory(orderServiceFactory)
        );
        // Resolved when the simulation starts, not here - instruments are often registered after
        // the broker is built.
        this.subscriptionManager = new NewCandleSubscriptionManager(
            candleRepository,
            () -> instrumentRepository.getAll(id)
                .stream()
                .map(Instrument::getUid)
                .collect(Collectors.toSet())
        );
    }

    /**
     * The order service's pending-order handler, which is also the simulation unit that fills
     * limit orders when their market event comes due - register it with the {@code EventSimulator}
     * alongside {@link #getSubscriptionManager()}. Always a
     * {@link SimulatedPendingOrderHandler}: every constructor routes through
     * {@link DefaultEventOrderServiceFactory}, which builds exactly that.
     */
    public SimulatedPendingOrderHandler getPendingOrderHandler() {
        return (SimulatedPendingOrderHandler) orderService.getPendingOrderHandler();
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
        protected String id = EventMockBroker.DEFAULT_ID;
        protected double commissionRatio = DEFAULT_COMMISSION_RATIO;
        protected DefaultEventOrderServiceFactory orderServiceFactory = new DefaultEventOrderServiceFactory();
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

        public Builder setOrderServiceFactory(DefaultEventOrderServiceFactory orderServiceFactory) {
            this.orderServiceFactory = orderServiceFactory;
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
                id,
                orderServiceFactory
            );
        }
    }
}
