package investtech.broker.impl.mock.simulation;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.exception.ErrorCode;
import investtech.broker.contract.service.exception.ExceptionBuilder;
import investtech.broker.contract.service.exception.InvalidRequestException;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.market.request.CandleInterval;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.request.*;
import investtech.broker.contract.service.order.response.*;
import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.broker.impl.mock.shared.exception.MockBrokerException;
import investtech.broker.impl.mock.shared.order.Order;
import investtech.simulation.Event;
import investtech.simulation.EventInvokerInterface;
import investtech.simulation.EventObserver;
import investtech.simulation.shared.market.candle.Candle;
import investtech.simulation.shared.market.candle.ComparisonOperator;
import investtech.simulation.shared.market.candle.FindPriceParams;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class MockOrderService implements OrderServiceInterface, EventInvokerInterface {
    protected MockBroker mockBroker;
    protected double buyBestPriceRatio = 49;
    protected double sellBestPriceRatio = 51;
    protected double orderCommissionRatio = 0.003;
    protected EventObserver eventObserver;
    protected Map<String, Map<String, Order>> orders = new HashMap<>();
    protected Duration limitOrderLiftTime = Duration.ofDays(1);
    protected Set<String> ordersRequestIds = new HashSet<>();

    public MockOrderService(MockBroker mockBroker) {
        this.mockBroker = mockBroker;
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        if (request.getPrice().isLessOrEqual(BigDecimal.ZERO)) {
            throw ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_PRICE);
        }

        if (request.getQuantity() <= 0) {
            throw ExceptionBuilder.create(ErrorCode.QUANTITY_MUST_BE_POSITIVE);
        }

        return switch (request.getDirection()) {
            case BUY -> buy(request);
            case SELL -> sell(request);
            case UNSPECIFIED -> throw new InvalidRequestException(ErrorCode.INVALID_PARAMETER_DIRECTION);
        };
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var cancelOrder = accountOrders.get(request.getOrderId());
        eventObserver.cancel(cancelOrder.getEvent());
        accountOrders.remove(request.getOrderId());

        return new CancelOrderResponse()
            .setTime(mockBroker.context.getCurrentTime());
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var accountOrder = accountOrders.get(request.getOrderId());

        if (null != request.getPriceType() && accountOrder.getRequest().getPriceType() != request.getPriceType()) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        return accountOrder.getState();
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders
            .computeIfAbsent(request.getAccountId(), k -> new HashMap<>())
            .values()
            .stream()
            .map(Order::getState)
            .collect(Collectors.toList());

        return new GetOrdersResponse().setOrders(accountOrders);
    }

    @Override
    public PostOrderResponse replace(ReplaceOrderRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var order = accountOrders.get(request.getOrderId());
        order
            .setRequestId(request.getIdempotencyKey())
            .setCreatedDate(mockBroker.context.getCurrentTime())
            .setReportStatus(ExecutionStatus.NEW);
        order.getRequest()
            .setOrderId(request.getOrderId())
            .setPrice(request.getPrice())
            .setQuantity(request.getQuantity())
            .setPriceType(request.getPriceType());
        var instrument = order.getInstrument();
        var instrumentPrice = calculateCurrentInstrumentPrice(
            instrument.getUid(),
            order.getRequest().getOrderType(),
            order.getRequest().getDirection()
        );
        order.setInitialPrice(instrumentPrice);

        registerOrder(order);

        return toServiceResponse(order);
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    public double getBuyBestPriceRatio() {
        return buyBestPriceRatio;
    }

    public MockOrderService setBuyBestPriceRatio(double buyBestPriceRatio) {
        this.buyBestPriceRatio = buyBestPriceRatio;
        return this;
    }

    public double getSellBestPriceRatio() {
        return sellBestPriceRatio;
    }

    public MockOrderService setSellBestPriceRatio(double sellBestPriceRatio) {
        this.sellBestPriceRatio = sellBestPriceRatio;
        return this;
    }

    public double getOrderCommissionRatio() {
        return orderCommissionRatio;
    }

    public void setOrderCommissionRatio(double orderCommission) {
        this.orderCommissionRatio = orderCommission;
    }

    protected PostOrderResponse registerDelayedOrder(Order order) throws AbstractException {
        var currentTime = mockBroker.context.getCurrentTime();
        var request = order.getRequest();

        var buySignalCandle = mockBroker.marketDataService.findCandlesByClosePrice(
            CandleInterval.HOUR,
            new FindPriceParams()
                .setFrom(currentTime)
                .setTo(currentTime.plus(limitOrderLiftTime))
                .setComparisonOperator(ComparisonOperator.MORE_OR_EQUAL)
                .setMaxCount(1)
                .setPrice(request.getPrice())
                .setInstrumentUid(request.getInstrumentId())
        ).stream().findFirst().orElse(null);

        var lastCandle = mockBroker.marketDataService.getCandleAt(
            request.getInstrumentId(),
            currentTime.plus(limitOrderLiftTime)
        ).orElse(null);

        if (lastCandle == null) {
            throw new MockBrokerException("Simulation error: last candle is null. Market data has ran out");
        }

        Instant eventTime;
        ExecutionStatus executionReportStatus;

        if (buySignalCandle != null) {
            eventTime = buySignalCandle.getTime();
            executionReportStatus = ExecutionStatus.FILL;
        } else {
            eventTime = lastCandle.getTime();
            executionReportStatus = ExecutionStatus.REJECTED;
        }

        order
            .setEvent(Event.create(eventTime, this))
            .setReportStatus(executionReportStatus);

        registerOrder(order);
        eventObserver.plan(order.getEvent());

        Money zeroMoney = Money.of(order.getInstrument().getCurrency(), Quotation.ZERO);

        return toServiceResponse(order)
            .setExecutedOrderPrice(zeroMoney)
            .setTotalOrderAmount(zeroMoney)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        calculateOrderPrices(order);

        return canBuyNow(request.getOrderType(), request.getPrice(), order.getTotalPrice())
            ? buyInstrument(order)
            : registerDelayedOrder(order);
    }

    protected boolean canBuyNow(OrderType orderType, Quotation priceLimit, Quotation currentPrice) {
        return OrderType.LIMIT != orderType || priceLimit.isMoreOrEqual(currentPrice);
    }

    protected PostOrderResponse sellInstrument(Order order) throws AbstractException {
        MockOperationsService operationsService = mockBroker.operationsService;

        var request = order.getRequest();
        order.setReportStatus(ExecutionStatus.FILL);

        calculateOrderPrices(order);
        registerOrder(order);

        operationsService.addMoney(
            request.getAccountId(), Money.of(order.getInstrument().getCurrency(), order.getTotalPrice())
        );
        operationsService.subtractFromPosition(
            request.getAccountId(),
            request.getInstrumentId(),
            request.getQuantity() * order.getInstrument().getLot()
        );

        return toServiceResponse(order);
    }

    protected PostOrderResponse buyInstrument(Order order) throws AbstractException {
        var operationsService = mockBroker.operationsService;
        order.setReportStatus(ExecutionStatus.FILL);

        registerOrder(order);

        var request = order.getRequest();
        var totalPriceMoney = Money.of(order.getInstrument().getCurrency(), order.getTotalPrice());
        var isEnoughOfMoney = operationsService.isEnoughOfMoney(
            request.getAccountId(),
            totalPriceMoney
        );

        if (isEnoughOfMoney) {
            operationsService.subtractMoney(
                request.getAccountId(), totalPriceMoney
            );
            operationsService.addToPosition(
                request.getAccountId(),
                request.getInstrumentId(),
                request.getQuantity() * order.getInstrument().getLot()
            );
        } else {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }

        return toServiceResponse(order);
    }

    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);

        return OrderType.LIMIT == request.getOrderType()
            && !request.getPrice().isMoreOrEqual(order.getInstrumentPrice())
            ? registerDelayedOrder(order)
            : sellInstrument(order);
    }

    protected Order createOrder(PostOrderRequest request) throws AbstractException {
        var instrument = mockBroker
            .instrumentService
            .get(GetRequest.of(request.getInstrumentId()))
            .getInstrument();

        var instrumentPrice = calculateCurrentInstrumentPrice(
            request.getInstrumentId(),
            request.getOrderType(),
            request.getDirection()
        );

        return new Order()
            .setRequest(request)
            .setCreatedDate(mockBroker.context.getCurrentTime())
            .setInstrument(instrument)
            .setInstrumentPrice(instrumentPrice);
    }

    protected Quotation calculateCurrentInstrumentPrice(String instrumentId, OrderType orderType, OrderDirection orderDirection) {
        var lastCandle = mockBroker.marketDataService.getInstrumentLastCandle(instrumentId);
        var bestPriceRatio = switch (orderDirection) {
            case BUY -> buyBestPriceRatio;
            case SELL -> sellBestPriceRatio;
            case UNSPECIFIED -> 1;
        };

        orderType = Objects.requireNonNullElse(orderType, OrderType.LIMIT);

        return switch (orderType) {
            case UNSPECIFIED, LIMIT, MARKET -> lastCandle.getOpenPrice();
            case BESTPRICE -> calculateBestPrice(lastCandle, bestPriceRatio);
        };
    }

    protected Quotation calculateOrderPrice(long quantity, long lots, Quotation instrumentPrice) {
        return instrumentPrice
            .multiply(BigDecimal.valueOf(lots))
            .multiply(BigDecimal.valueOf(quantity));
    }

    protected Quotation calculateBestPrice(Candle candle, double bestPriceRatio) {
        return candle
            .getHighPrice()
            .subtract(candle.getLowPrice())
            .multiply(BigDecimal.valueOf(bestPriceRatio))
            .add(candle.getLowPrice());
    }

    protected void assertAccountAvailable(String accountId) throws AbstractException {
        var accountState = mockBroker.userService.getAccountState(accountId);

        if (accountState == null) {
            throw ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        if (accountState.isBlocked()) {
            throw ExceptionBuilder.create(ErrorCode.ACCOUNT_BLOCKED);
        }

        if (accountState.isClosed()) {
            throw ExceptionBuilder.create(ErrorCode.ACCOUNT_CLOSED);
        }
    }

    protected PostOrderResponse toServiceResponse(Order order) {
        var currency = order.getInstrument().getCurrency();
        var commissionPriceMoney = Money.of(currency, order.getCommissionPrice());

        return new PostOrderResponse()
            .setOrderId(order.getRequest().getOrderId())
            .setDirection(order.getRequest().getDirection())
            .setExecutedOrderPrice(Money.of(currency, order.getExecutedPrice()))
            .setExecutedCommission(commissionPriceMoney)
            .setInitialCommission(commissionPriceMoney)
            .setInstrumentUid(order.getInstrument().getUid())
            .setOrderRequestId(order.getRequestId())
            .setOrderType(order.getRequest().getOrderType())
            .setLotsExecuted(order.getRequest().getQuantity())
            .setLotsRequested(order.getRequest().getQuantity())
            .setTotalOrderAmount(Money.of(currency, order.getTotalPrice()))
            .setInitialOrderPrice(Money.of(currency, order.getInitialPrice()))
            .setInitialSecurityPrice(Money.of(currency, order.getInitialPricePerOne()))
            .setExecutionStatus(order.getReportStatus());
    }

    protected void calculateOrderPrices(Order order) {
        var instrument = order.getInstrument();
        var instrumentPrice = order.getInstrumentPrice();

        var quantity = order.getRequest().getQuantity();
        var lotSize = instrument.getLot();

        var initialPrice = calculateOrderPrice(quantity, lotSize, instrumentPrice);
        var commissionPrice = initialPrice.multiply(Quotation.of(orderCommissionRatio));
        var totalPrice = initialPrice.add(commissionPrice);
        var executedPrice = totalPrice.divide(BigDecimal.valueOf(quantity * lotSize));

        order
            .setCommissionPrice(commissionPrice)
            .setInitialPrice(initialPrice)
            .setInitialPricePerOne(instrumentPrice)
            .setTotalPrice(totalPrice)
            .setExecutedPrice(executedPrice);
    }

    protected void registerOrder(Order order) throws AbstractException {
        if (null == order.getRequestId()) {
            order.setRequestId(UUID.randomUUID().toString());
        } else if (ordersRequestIds.contains(order.getRequestId())) {
            throw ExceptionBuilder.create(ErrorCode.DUPLICATE_ORDER);
        }

        if (null == order.getRequest().getOrderId()) {
            order.getRequest().setOrderId(UUID.randomUUID().toString());
        }

        ordersRequestIds.add(order.getRequestId());

        orders
            .computeIfAbsent(order.getRequest().getAccountId(), x -> new HashMap<>())
            .put(order.getRequest().getOrderId(), order);
    }
}
