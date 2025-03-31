package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.order.Order;
import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvokerInterface;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnitInterface;
import com.siberalt.singularity.simulation.shared.market.candle.Candle;
import com.siberalt.singularity.simulation.shared.market.candle.ComparisonOperator;
import com.siberalt.singularity.simulation.shared.market.candle.FindPriceParams;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class EventSimulatedOrderService extends MockOrderService implements EventInvokerInterface, TimeDependentUnitInterface {
    private static final Logger logger = Logger.getLogger(EventSimulatedOrderService.class.getName());
    protected EventObserver eventObserver;
    protected EventMockBroker mockBroker;
    protected Map<String, OrderEvent> orderEvents = new HashMap<>();
    protected Map<Instant, List<OrderEvent>> orderEventsByTime = new HashMap<>();

    public EventSimulatedOrderService(EventMockBroker mockBroker) {
        super(mockBroker);
        this.mockBroker = mockBroker;
    }

    @Override
    public void tick(Clock clock) {
        var currentTime = clock.currentTime();

        if (orderEventsByTime.containsKey(currentTime)) {
            for (var orderEvent : orderEventsByTime.get(currentTime)) {
                logger.info(
                    String.format("[%s] Executing order event: %s", clock.currentTime(), orderEvent.getEvent().getId())
                );
                Order order = orderEvent.getOrder();

                try {
                    if (order.getDirection() == OrderDirection.BUY) {
                        buyInstrument(order);
                    } else {
                        sellInstrument(order);
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
    protected void buyInstrument(Order order) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Buying instrument %s, Amount: %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                mockBroker.context.getCurrentTime(),
                order.getInstrument().getUid(),
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                order.getTotalPrice(),
                order.getCommissionPrice()
            )
        );
        super.buyInstrument(order);
    }

    @Override
    protected PostOrderResponse sellInstrument(Order order) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Selling instrument %s, Amount: %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                mockBroker.context.getCurrentTime(),
                order.getInstrument().getUid(),
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                order.getTotalPrice(),
                order.getCommissionPrice()
            )
        );
        return super.sellInstrument(order);
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        calculateOrderPrices(order);
        checkEnoughOfMoneyToBuy(order);

        if (canBuyNow(request.getOrderType(), request.getPrice(), order.getInitialPricePerOne())) {
            buyInstrument(order);
        } else {
            logger.info(
                String.format(
                    "[%s] Predicting market event for order %s",
                    mockBroker.context.getCurrentTime(),
                    order.getOrderId()
                )
            );
            predictMarketEvent(order);
        }

        return toServiceResponse(order);
    }

    @Override
    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        calculateOrderPrices(order);
        checkEnoughOfPositionToSell(order);

        if (canSellNow(request.getOrderType(), request.getPrice(), order.getInstrumentPrice())) {
            sellInstrument(order);
        } else {
            logger.info(
                String.format(
                    "[%s] Predicting market event for order %s",
                    mockBroker.context.getCurrentTime(),
                    order.getOrderId()
                )
            );
            predictMarketEvent(order);
        }

        return toServiceResponse(order);
    }

    protected void predictMarketEvent(Order order) throws AbstractException {
        var currentTime = mockBroker.context.getCurrentTime();
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

        Order newOrderState = new Order()
            .setAccountId(order.getAccountId())
            .setOrderId(order.getOrderId())
            .setDirection(order.getDirection())
            .setOrderType(order.getOrderType())
            .setLotsRequested(order.getLotsRequested())
            .setRequestedPrice(order.getRequestedPrice())
            .setCreatedDate(currentTime)
            .setInitialPrice(order.getInitialPrice())
            .setInitialPricePerOne(order.getInitialPricePerOne())
            .setTotalPricePerOne(order.getTotalPricePerOne())
            .setTotalPrice(order.getTotalPrice())
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
            calculateOrderPrices(newOrderState.setInstrumentPrice(instrumentPrice));
        } else {
            var lastCandle = mockBroker.marketDataService.getCandleAt(
                instrument.getUid(),
                endOrderLifeTime
            ).orElse(null);

            if (lastCandle == null) {
                throw new MockBrokerException("Simulation error: last candle is null. Market data has ran out");
            }

            eventTime = lastCandle.getTime();
            executionStatus = ExecutionStatus.REJECTED;
        }

        newOrderState.setExecutionStatus(executionStatus);

        var event = Event.create(eventTime, this);
        var orderEvent = new OrderEvent(newOrderState, event);
        orderEvents.put(newOrderState.getOrderId(), orderEvent);
        orderEventsByTime.computeIfAbsent(eventTime, k -> new ArrayList<>()).add(orderEvent);

        order
            .setTotalPricePerOne(Quotation.ZERO)
            .setTotalPrice(Quotation.ZERO)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);

        registerOrder(order);
        eventObserver.scheduleEvent(event);
    }

    @Override
    protected void cancel(Order order) {
        super.cancel(order);
        eventObserver.cancelEvent(orderEvents.get(order.getOrderId()).getEvent());
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
                new FindPriceParams()
                    .setFrom(from)
                    .setTo(to)
                    .setComparisonOperator(comparisonOperator)
                    .setMaxCount(1)
                    .setPrice(requestedPrice)
                    .setInstrumentUid(instrumentUid)
            )
            .stream()
            .findFirst()
            .orElse(null);
    }
}
