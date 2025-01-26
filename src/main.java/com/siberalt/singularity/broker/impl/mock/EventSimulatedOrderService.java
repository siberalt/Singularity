package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
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
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvokerInterface;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.shared.market.candle.Candle;
import com.siberalt.singularity.simulation.shared.market.candle.ComparisonOperator;
import com.siberalt.singularity.simulation.shared.market.candle.FindPriceParams;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class EventSimulatedOrderService extends MockOrderService implements EventInvokerInterface {
    protected EventObserver eventObserver;
    protected EventMockBroker mockBroker;
    protected Map<String, OrderEvent> orderEvents = new HashMap<>();

    public EventSimulatedOrderService(EventMockBroker mockBroker) {
        super(mockBroker);
        this.mockBroker = mockBroker;
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        calculateOrderPrices(order);
        checkEnoughOfMoneyToBuy(order);

        if (canBuyNow(request.getOrderType(), request.getPrice(), order.getInitialPricePerOne())) {
            buyInstrument(order);
        } else {
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
            predictMarketEvent(order);
        }

        return toServiceResponse(order);
    }

    protected void predictMarketEvent(Order order) throws AbstractException {
        var currentTime = mockBroker.context.getCurrentTime();
        var endOrderLifeTime = currentTime.plus(limitOrderLifeTime);
        Instrument instrument = order.getInstrument();

        var marketSignalCandle = findMarketSignalCandle(
            order.getDirection(),
            order.getRequestedPrice(),
            instrument.getUid(),
            currentTime,
            endOrderLifeTime
        );

        Instant eventTime;
        ExecutionStatus executionStatus;

        if (marketSignalCandle != null) {
            eventTime = marketSignalCandle.getTime();
            executionStatus = ExecutionStatus.FILL;
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

        Order newOrderState = new Order()
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
            .setExecutionStatus(executionStatus)
            .setInstrumentPrice(order.getInstrumentPrice());

        var orderEvent = Event.create(eventTime, this);
        orderEvents.put(newOrderState.getOrderId(), new OrderEvent(newOrderState, orderEvent));

        order
            .setTotalPricePerOne(Quotation.ZERO)
            .setTotalPrice(Quotation.ZERO)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);

        registerOrder(order);
        eventObserver.plan(orderEvent);
    }

    @Override
    protected void cancel(Order order) {
        super.cancel(order);
        eventObserver.cancel(orderEvents.get(order.getOrderId()).getEvent());
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
