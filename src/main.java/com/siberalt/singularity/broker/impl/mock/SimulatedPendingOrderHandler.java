package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvoker;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnit;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fills orders that have to wait for the market, by looking ahead in the candle history instead of
 * re-checking on every tick: the first candle whose open crosses the limit price becomes a
 * scheduled event, and the order is filled when the simulation reaches it. An order whose price is
 * never reached within its lifetime is scheduled as rejected instead.
 */
public class SimulatedPendingOrderHandler implements PendingOrderHandler, EventInvoker, TimeDependentUnit {
    private static final Logger logger = Logger.getLogger(SimulatedPendingOrderHandler.class.getName());

    protected final OrderExecutor orderExecutor;
    protected final OrderRegistry orderRegistry;
    protected final OrderPriceModel priceModel;
    protected final MockMarketDataService marketDataService;
    protected final Map<String, OrderEvent> orderEvents = new HashMap<>();
    protected final Map<Instant, List<OrderEvent>> orderEventsByTime = new HashMap<>();
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected EventObserver eventObserver;
    protected Clock clock;

    public SimulatedPendingOrderHandler(
        Clock clock,
        OrderExecutor orderExecutor,
        OrderRegistry orderRegistry,
        OrderPriceModel priceModel,
        MockMarketDataService marketDataService
    ) {
        this.clock = clock;
        this.orderExecutor = orderExecutor;
        this.orderRegistry = orderRegistry;
        this.priceModel = priceModel;
        this.marketDataService = marketDataService;
    }

    public Duration getLimitOrderLifeTime() {
        return limitOrderLifeTime;
    }

    public SimulatedPendingOrderHandler setLimitOrderLifeTime(Duration limitOrderLifeTime) {
        this.limitOrderLifeTime = limitOrderLifeTime;
        return this;
    }

    /**
     * The simulator's clock is authoritative once a run starts. It is expected to be the same clock
     * the broker was built with - scheduling decisions made here and the timestamps the executor
     * stamps on fills have to agree.
     */
    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    @Override
    public void onNotFillable(Order order) throws AbstractException {
        logger.info(
            String.format("[%s] Predicting market event for order %s", clock.currentTime(), order.getId())
        );

        var currentTime = clock.currentTime();
        var endOrderLifeTime = currentTime.plus(limitOrderLifeTime);
        Instrument instrument = order.getInstrument();

        Candle marketSignalCandle = findMarketSignalCandle(
            order.getDirection(),
            order.getRequestedPrice(),
            instrument.getUid(),
            currentTime,
            endOrderLifeTime
        );

        Instant eventTime;
        ExecutionStatus executionStatus;

        Order futureOrder = new Order()
            .setAccountId(order.getAccountId())
            .setId(order.getId())
            // Same logical order, so it keeps the key: the fill replaces the scheduled order in
            // the repository instead of leaving the original key entry behind.
            .setIdempotencyKey(order.getIdempotencyKey())
            .setDirection(order.getDirection())
            .setOrderType(order.getOrderType())
            .setLotsRequested(order.getLotsRequested())
            .setRequestedPrice(order.getRequestedPrice())
            .setCreatedTime(currentTime)
            .setInstrument(order.getInstrument())
            .setInstrumentPrice(order.getInstrumentPrice());

        if (marketSignalCandle != null) {
            eventTime = marketSignalCandle.getTime();
            executionStatus = ExecutionStatus.FILL;
            futureOrder.setInstrumentPrice(
                priceModel.currentPrice(order.getOrderType(), order.getDirection(), marketSignalCandle)
            );
        } else {
            var lastCandle = marketDataService.findClosestBefore(
                instrument.getUid(),
                endOrderLifeTime
            ).orElse(null);

            if (lastCandle == null) {
                throw new MockBrokerException("Simulation error: last candle is null. Market data has ran out");
            }

            eventTime = lastCandle.getTime();
            executionStatus = ExecutionStatus.REJECTED;
        }

        futureOrder.setExecutionStatus(executionStatus);

        Event event = Event.create(eventTime, this);
        OrderEvent orderEvent = new OrderEvent(futureOrder, event);
        orderEvents.put(futureOrder.getId(), orderEvent);
        orderEventsByTime.computeIfAbsent(eventTime, k -> new ArrayList<>()).add(orderEvent);

        order
            .setBalanceChange(Quotation.ZERO)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);

        orderRegistry.register(order);
        eventObserver.scheduleEvent(event);
    }

    /**
     * Drops the fill scheduled for this order. Marking it cancelled and journalling the cancel
     * operation has already been done by the caller - this only undoes the scheduling.
     */
    @Override
    public void onCancelled(Order order) {
        // An order that filled immediately never got a scheduled event.
        OrderEvent orderEvent = orderEvents.remove(order.getId());

        if (orderEvent == null) {
            return;
        }

        eventObserver.cancelEvent(orderEvent.getEvent());
        orderEventsByTime.computeIfPresent(
            orderEvent.getEvent().getTimePoint(),
            (time, events) -> {
                events.remove(orderEvent);
                return events.isEmpty() ? null : events;
            }
        );
    }

    @Override
    public void tick() {
        var currentTime = clock.currentTime();

        if (!orderEventsByTime.containsKey(currentTime)) {
            return;
        }

        for (var orderEvent : orderEventsByTime.get(currentTime)) {
            logger.info(
                String.format("[%s] Executing order event: %s", currentTime, orderEvent.getEvent().getId())
            );

            Order order = orderEvent.getOrder();
            // The event is consumed here either way, so drop it from the by-id index too -
            // otherwise it accumulates for the whole simulation.
            orderEvents.remove(order.getId());

            try {
                List<TransactionSpec> transactionSpecs = orderExecutor.calculateTransactions(order);

                if (order.getDirection() == OrderDirection.BUY) {
                    orderExecutor.buy(order, transactionSpecs);
                } else {
                    orderExecutor.sell(order, transactionSpecs);
                }
            } catch (AbstractException e) {
                logger.severe(
                    String.format(
                        "[%s] Error while executing order: %s",
                        currentTime,
                        orderEvent.getEvent().getId()
                    )
                );
                throw new MockBrokerException("Error while executing order", e);
            }
        }

        orderEventsByTime.remove(currentTime);
    }

    protected Candle findMarketSignalCandle(
        OrderDirection orderDirection,
        Quotation requestedPrice,
        String instrumentUid,
        Instant from,
        Instant to
    ) {
        ComparisonOperator comparisonOperator = orderDirection == OrderDirection.BUY
            ? ComparisonOperator.MORE_OR_EQUAL
            : ComparisonOperator.LESS_OR_EQUAL;

        return marketDataService.findCandlesByOpenPrice(
                CandleInterval.MIN_1,
                new FindPriceParams(
                    instrumentUid,
                    from,
                    to,
                    requestedPrice,
                    comparisonOperator,
                    1
                )
            )
            .stream()
            .findFirst()
            .orElse(null);
    }
}
