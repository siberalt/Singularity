package investtech.broker.impl.mock;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.request.CandleInterval;
import investtech.broker.contract.service.order.request.PostOrderRequest;
import investtech.broker.contract.service.order.response.ExecutionStatus;
import investtech.broker.contract.service.order.response.PostOrderResponse;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.broker.impl.mock.shared.exception.MockBrokerException;
import investtech.broker.impl.mock.shared.order.Order;
import investtech.broker.impl.mock.shared.order.OrderEvent;
import investtech.simulation.Event;
import investtech.simulation.EventInvokerInterface;
import investtech.simulation.EventObserver;
import investtech.simulation.shared.market.candle.ComparisonOperator;
import investtech.simulation.shared.market.candle.FindPriceParams;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class EventMockOrderService extends MockOrderService implements EventInvokerInterface {
    protected EventObserver eventObserver;
    protected EventMockBroker mockBroker;
    protected Map<Instant, OrderEvent> orderEvents = new HashMap<>();

    public EventMockOrderService(EventMockBroker mockBroker) {
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

        return canBuyNow(request.getOrderType(), request.getPrice(), order.getTotalPrice())
            ? buyInstrument(order)
            : predictBuyEvent(order);
    }

    protected PostOrderResponse predictBuyEvent(Order order) throws AbstractException {
        var currentTime = mockBroker.context.getCurrentTime();
        var endOrderLifeTime = currentTime.plus(limitOrderLifeTime);
        var request = order.getRequest();

        var buySignalCandle = mockBroker.marketDataService.findCandlesByOpenPrice(
                CandleInterval.MIN_1,
                new FindPriceParams()
                    .setFrom(currentTime)
                    .setTo(endOrderLifeTime)
                    .setComparisonOperator(ComparisonOperator.MORE_OR_EQUAL)
                    .setMaxCount(1)
                    .setPrice(request.getPrice())
                    .setInstrumentUid(request.getInstrumentId())
            )
            .stream()
            .findFirst()
            .orElse(null);

        var lastCandle = mockBroker.marketDataService.getCandleAt(
            request.getInstrumentId(),
            endOrderLifeTime
        ).orElse(null);

        if (lastCandle == null) {
            throw new MockBrokerException("Simulation error: last candle is null. Market data has ran out");
        }

        Instant eventTime;
        ExecutionStatus executionStatus;

        if (buySignalCandle != null) {
            eventTime = buySignalCandle.getTime();
            executionStatus = ExecutionStatus.FILL;
        } else {
            eventTime = lastCandle.getTime();
            executionStatus = ExecutionStatus.REJECTED;
        }

        var eventOrder = new Order()
            .setRequest(request)
            .setCreatedDate(currentTime)
            .setInitialPrice(order.getInitialPrice())
            .setInitialPricePerOne(order.getInitialPricePerOne())
            .setInstrument(order.getInstrument())
            .setExecutionStatus(executionStatus)
            .setInstrumentPrice(order.getInstrumentPrice());

        var orderEvent = Event.create(eventTime, this);
        orderEvents.put(eventTime, new OrderEvent(eventOrder , orderEvent));

        order
            .setTotalPricePerOne(Quotation.ZERO)
            .setTotalPrice(Quotation.ZERO)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);

        registerOrder(order);
        eventObserver.plan(orderEvent);

        return toServiceResponse(order);
    }
}
