package investtech.broker.impl.mock;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.exception.ErrorCode;
import investtech.broker.contract.service.exception.ExceptionBuilder;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.request.*;
import investtech.broker.contract.service.order.response.*;
import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.broker.impl.mock.shared.order.Order;
import investtech.simulation.shared.market.candle.Candle;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class MockOrderService implements OrderServiceInterface {
    protected MockBroker mockBroker;
    protected double buyBestPriceRatio = 0.3;
    protected double sellBestPriceRatio = 0.7;
    protected double orderCommissionRatio = 0.003;
    protected Map<String, Map<String, Order>> orders = new HashMap<>();
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected Set<String> ordersRequestIds = new HashSet<>();

    public MockOrderService(MockBroker mockBroker) {
        this.mockBroker = mockBroker;
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        if (null == request.getPrice() && OrderType.LIMIT == request.getOrderType()) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_PRICE);
        }

        if (null != request.getPrice() && request.getPrice().isLessOrEqual(BigDecimal.ZERO)) {
            throw ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_PRICE);
        }

        if (request.getQuantity() <= 0) {
            throw ExceptionBuilder.create(ErrorCode.QUANTITY_MUST_BE_POSITIVE);
        }

        return switch (request.getDirection()) {
            case BUY -> buy(request);
            case SELL -> sell(request);
            case UNSPECIFIED -> throw ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_DIRECTION);
        };
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var cancelOrder = accountOrders.get(request.getOrderId());
        cancel(cancelOrder);

        return new CancelOrderResponse()
            .setTime(mockBroker.context.getCurrentTime());
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

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
        checkAccountAvailable(request.getAccountId());

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
        checkAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var order = accountOrders.get(request.getOrderId());
        order
            .setRequestId(request.getIdempotencyKey())
            .setCreatedDate(mockBroker.context.getCurrentTime())
            .setExecutionStatus(ExecutionStatus.NEW);
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

    public Duration getLimitOrderLifeTime() {
        return limitOrderLifeTime;
    }

    public MockOrderService setLimitOrderLifeTime(Duration limitOrderLifeTime) {
        this.limitOrderLifeTime = limitOrderLifeTime;
        return this;
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

    protected void cancel(Order order) throws AbstractException {
        if (
            order.getExecutionStatus() == ExecutionStatus.FILL
                || order.getExecutionStatus() == ExecutionStatus.CANCELLED
        ) {
            throw ExceptionBuilder.create(ErrorCode.CANCEL_ORDER_ERROR);
        }

        order
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.CANCELLED);
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        calculateOrderPrices(order);
        checkEnoughOfMoneyToBuy(order);

        if (!canBuyNow(request.getOrderType(), request.getPrice(), order.getInitialPricePerOne())) {
            throw ExceptionBuilder
                .newBuilder(ErrorCode.UNIMPLEMENTED)
                .withMessage("Limit orders are not implemented yet")
                .build();
        }

        return buyInstrument(order);
    }

    protected boolean canBuyNow(OrderType orderType, Quotation priceLimit, Quotation currentPrice) {
        return OrderType.LIMIT != orderType || priceLimit.isMoreOrEqual(currentPrice);
    }

    protected boolean canSellNow(OrderType orderType, Quotation priceLimit, Quotation currentPrice) {
        return OrderType.LIMIT != orderType || priceLimit.isLessOrEqual(currentPrice);
    }

    protected PostOrderResponse sellInstrument(Order order) throws AbstractException {
        MockOperationsService operationsService = mockBroker.operationsService;

        var request = order.getRequest();
        order
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());

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
        order
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());

        registerOrder(order);

        var request = order.getRequest();
        var totalPriceMoney = Money.of(order.getInstrument().getCurrency(), order.getTotalPrice());

        operationsService.subtractMoney(
            request.getAccountId(), totalPriceMoney
        );
        operationsService.addToPosition(
            request.getAccountId(),
            request.getInstrumentId(),
            request.getQuantity() * order.getInstrument().getLot()
        );

        return toServiceResponse(order);
    }

    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);
        calculateOrderPrices(order);
        checkEnoughOfPositionToSell(order);

        if (!canSellNow(request.getOrderType(), request.getPrice(), order.getInstrumentPrice())) {
            throw ExceptionBuilder
                .newBuilder(ErrorCode.UNIMPLEMENTED)
                .withMessage("Limit orders are not implemented yet")
                .build();
        }

        return sellInstrument(order);
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
            .setLotsRequested(request.getQuantity())
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
        Quotation priceRange = candle.getHighPrice().subtract(candle.getLowPrice());

        return candle
            .getLowPrice()
            .add(priceRange.multiply(BigDecimal.valueOf(bestPriceRatio)));
    }

    protected void checkAccountAvailable(String accountId) throws AbstractException {
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

    protected void checkEnoughOfMoneyToBuy(Order order) throws AbstractException {
        var isEnoughOfMoney = this.mockBroker.operationsService.isEnoughOfMoney(
            order.getRequest().getAccountId(),
            Money.of(order.getInstrument().getCurrency(), order.getTotalPrice())
        );

        if (!isEnoughOfMoney) {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    protected void checkEnoughOfPositionToSell(Order order) throws AbstractException {
        var position = mockBroker.operationsService.getPositionByInstrumentId(
            order.getRequest().getAccountId(),
            order.getRequest().getInstrumentId()
        );

        if (position.getBalance() < order.getRequest().getQuantity() * order.getInstrument().getLot()) {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    protected PostOrderResponse toServiceResponse(Order order) {
        var currency = order.getInstrument().getCurrency();
        var commissionPriceMoney = Money.of(currency, order.getCommissionPrice());

        return new PostOrderResponse()
            .setOrderId(order.getRequest().getOrderId())
            .setDirection(order.getRequest().getDirection())
            .setTotalPricePerOne(Money.of(currency, order.getTotalPricePerOne()))
            .setExecutedCommission(commissionPriceMoney)
            .setInitialCommission(commissionPriceMoney)
            .setInstrumentUid(order.getInstrument().getUid())
            .setOrderRequestId(order.getRequestId())
            .setOrderType(order.getRequest().getOrderType())
            .setLotsExecuted(order.getLotsExecuted())
            .setLotsRequested(order.getLotsRequested())
            .setTotalPrice(Money.of(currency, order.getTotalPrice()))
            .setInitialPrice(Money.of(currency, order.getInitialPrice()))
            .setInitialPricePerOne(Money.of(currency, order.getInitialPricePerOne()))
            .setExecutionStatus(order.getExecutionStatus());
    }

    protected void calculateOrderPrices(Order order) {
        var instrument = order.getInstrument();
        var instrumentPrice = order.getInstrumentPrice();

        var quantity = order.getRequest().getQuantity();
        var lotSize = instrument.getLot();

        var initialPrice = calculateOrderPrice(quantity, lotSize, instrumentPrice);
        var commissionPrice = initialPrice.multiply(Quotation.of(orderCommissionRatio));
        var totalPrice = initialPrice.add(commissionPrice);
        var totalPricePerOne = totalPrice.divide(BigDecimal.valueOf(quantity * lotSize));

        order
            .setCommissionPrice(commissionPrice)
            .setInitialPrice(initialPrice)
            .setInitialPricePerOne(instrumentPrice)
            .setTotalPrice(totalPrice)
            .setTotalPricePerOne(totalPricePerOne);
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
