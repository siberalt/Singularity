package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvoker;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnit;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class EventSimulatedOrderService extends MockOrderService implements EventInvoker, TimeDependentUnit {
    private static final Logger logger = Logger.getLogger(EventSimulatedOrderService.class.getName());
    protected EventObserver eventObserver;
    protected EventMockBroker mockBroker;
    protected Map<String, OrderEvent> orderEvents = new HashMap<>();
    protected Map<Instant, List<OrderEvent>> orderEventsByTime = new HashMap<>();
    protected Clock clock;

    public EventSimulatedOrderService(EventMockBroker mockBroker, OrderRepository orderRepository) {
        super(mockBroker, orderRepository);
        this.mockBroker = mockBroker;
    }

    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void tick() {
        var currentTime = clock.currentTime();

        if (orderEventsByTime.containsKey(currentTime)) {
            for (var orderEvent : orderEventsByTime.get(currentTime)) {
                logger.info(
                    String.format("[%s] Executing order event: %s", clock.currentTime(), orderEvent.getEvent().getId())
                );

                try {
                    Order order = orderEvent.getOrder();
                    List<TransactionSpec> transactionSpecs = calculateTransactions(order);

                    if (order.getDirection() == OrderDirection.BUY) {
                        buyInstrument(order, transactionSpecs);
                    } else {
                        sellInstrument(order, transactionSpecs);
                    }
                } catch (AbstractException e) {
                    logger.severe(
                        String.format(
                            "[%s] Error while executing order: %s",
                            clock.currentTime(),
                            orderEvent.getEvent().getId()
                        )
                    );
                    throw new MockBrokerException("Error while executing order", e);
                }
            }

            orderEventsByTime.remove(currentTime);
        }
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    @Override
    protected void buyInstrument(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Buying instrument %s, Amount: %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                mockBroker.clock.currentTime(),
                order.getInstrument().getUid(),
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                order.getBalanceChange(),
                transactionSpecs
            )
        );
        super.buyInstrument(order, transactionSpecs);
    }

    @Override
    protected PostOrderResponse sellInstrument(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Selling instrument %s, Amount: %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                mockBroker.clock.currentTime(),
                order.getInstrument().getUid(),
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                order.getBalanceChange(),
                transactionSpecs
            )
        );
        return super.sellInstrument(order, transactionSpecs);
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        List<TransactionSpec> transactionSpecs = calculateTransactions(order);
        checkEnoughOfMoneyToBuy(order);

        if (canBuyNow(order)) {
            buyInstrument(order, transactionSpecs);
        } else {
            logger.info(
                String.format(
                    "[%s] Predicting market event for order %s",
                    mockBroker.clock.currentTime(),
                    order.getId()
                )
            );
            predictMarketEvent(order);
        }

        return toServiceResponse(order);
    }

    @Override
    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);
        List<TransactionSpec> transactionSpecs = calculateTransactions(order);
        checkEnoughOfPositionToSell(order);

        if (canSellNow(order)) {
            sellInstrument(order, transactionSpecs);
        } else {
            logger.info(
                String.format(
                    "[%s] Predicting market event for order %s",
                    mockBroker.clock.currentTime(),
                    order.getId()
                )
            );
            predictMarketEvent(order);
        }

        return toServiceResponse(order);
    }

    protected void predictMarketEvent(Order order) throws AbstractException {
        var currentTime = mockBroker.clock.currentTime();
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
            Quotation instrumentPrice = calculateCurrentPrice(
                order.getOrderType(),
                order.getDirection(),
                marketSignalCandle
            );
            futureOrder.setInstrumentPrice(instrumentPrice);
        } else {
            var lastCandle = mockBroker.marketDataService.findClosestBefore(
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

        registerOrder(order);
        eventObserver.scheduleEvent(event);
    }

    @Override
    protected void cancel(Order order) {
        super.cancel(order);
        eventObserver.cancelEvent(orderEvents.get(order.getId()).getEvent());
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

        return mockBroker.marketDataService.findCandlesByOpenPrice(
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
