package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.*;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.order.OrderServiceInterface;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.order.Order;
import com.siberalt.singularity.broker.impl.mock.shared.user.AccountState;
import com.siberalt.singularity.simulation.shared.market.candle.Candle;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class MockOrderService implements OrderServiceInterface {
    protected MockBroker mockBroker;
    protected double buyBestPriceRatio = 0.3;
    protected double sellBestPriceRatio = 0.7;
    protected double commissionRatio = 0.003;
    protected Map<String, Map<String, Order>> orders = new HashMap<>();
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected Set<String> ordersRequestIds = new HashSet<>();

    public MockOrderService(MockBroker mockBroker) {
        this.mockBroker = mockBroker;
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        if (request.getPrice() == null && OrderType.LIMIT == request.getOrderType()) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_PRICE);
        }

        if (request.getPrice() != null && request.getPrice().isLessOrEqual(BigDecimal.ZERO)) {
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

        Map<String, Order> accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        Order cancelOrder = accountOrders.get(request.getOrderId());

        if (cancelOrder.getExecutionStatus() != ExecutionStatus.NEW) {
            throw ExceptionBuilder.create(ErrorCode.CANCEL_ORDER_ERROR);
        }

        cancel(cancelOrder);

        return new CancelOrderResponse()
            .setTime(mockBroker.context.getCurrentTime());
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        Map<String, Order> accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        Order accountOrder = accountOrders.get(request.getOrderId());

        if (request.getPriceType() != null && accountOrder.getPriceType() != request.getPriceType()) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        return accountOrder.getState();
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        List<OrderState> accountOrders = orders
            .computeIfAbsent(request.getAccountId(), k -> new HashMap<>())
            .values()
            .stream()
            .map(Order::getState)
            .collect(Collectors.toList());

        return new GetOrdersResponse().setOrders(accountOrders);
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

    public double getCommissionRatio() {
        return commissionRatio;
    }

    public void setCommissionRatio(double commissionRatio) {
        this.commissionRatio = commissionRatio;
    }

    protected void cancel(Order order) {
        order
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.CANCELLED);
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);
        calculateOrderPrices(order);
        checkEnoughOfMoneyToBuy(order);

        if (!canBuyNow(request.getOrderType(), request.getPrice(), order.getInitialPricePerOne())) {
            throw ExceptionBuilder
                .newBuilder(ErrorCode.UNIMPLEMENTED)
                .withMessage("Limit orders are not implemented yet")
                .build();
        }

        buyInstrument(order);

        return toServiceResponse(order);
    }

    protected boolean canBuyNow(OrderType orderType, Quotation priceLimit, Quotation currentPrice) {
        return OrderType.LIMIT != orderType || priceLimit.isMoreOrEqual(currentPrice);
    }

    protected boolean canSellNow(OrderType orderType, Quotation priceLimit, Quotation currentPrice) {
        return OrderType.LIMIT != orderType || priceLimit.isLessOrEqual(currentPrice);
    }

    protected PostOrderResponse sellInstrument(Order order) throws AbstractException {
        MockOperationsService operationsService = mockBroker.operationsService;

        order
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());

        calculateOrderPrices(order);
        registerOrder(order);

        operationsService.addMoney(
            order.getAccountId(), Money.of(order.getInstrument().getCurrency(), order.getTotalPrice())
        );
        operationsService.subtractFromPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );

        return toServiceResponse(order);
    }

    protected void buyInstrument(Order order) throws AbstractException {
        MockOperationsService operationsService = mockBroker.operationsService;
        order
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());

        registerOrder(order);

        Money totalPriceMoney = Money.of(order.getInstrument().getCurrency(), order.getTotalPrice());

        operationsService.subtractMoney(
            order.getAccountId(), totalPriceMoney
        );
        operationsService.addToPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );
    }

    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);
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
        Instrument instrument = mockBroker
            .instrumentService
            .get(GetRequest.of(request.getInstrumentId()))
            .getInstrument();

        if (instrument == null) {
            throw ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND);
        }

        Candle currentCandle = mockBroker.marketDataService.getInstrumentCurrentCandle(request.getInstrumentId());
        if (currentCandle == null) {
            throw new MockBrokerException("Candle not found");
        }

        Quotation instrumentPrice = calculateCurrentPrice(request.getOrderType(), request.getDirection(), currentCandle);

        return new Order()
            .setOrderId(UUID.randomUUID().toString())
            .setCreatedDate(mockBroker.context.getCurrentTime())
            .setLotsRequested(request.getQuantity())
            .setAccountId(request.getAccountId())
            .setDirection(request.getDirection())
            .setOrderType(request.getOrderType())
            .setInstrument(instrument)
            .setInstrumentPrice(instrumentPrice);
    }

    protected Quotation calculateCurrentPrice(OrderType orderType, OrderDirection orderDirection, Candle currentCandle) {
        double bestPriceRatio = switch (orderDirection) {
            case BUY -> buyBestPriceRatio;
            case SELL -> sellBestPriceRatio;
            case UNSPECIFIED -> 1;
        };

        orderType = Objects.requireNonNullElse(orderType, OrderType.LIMIT);

        return switch (orderType) {
            case UNSPECIFIED, LIMIT, MARKET -> currentCandle.getOpenPrice();
            case BEST_PRICE -> calculateBestPrice(currentCandle, bestPriceRatio);
        };
    }

    protected Quotation calculateBestPrice(Candle candle, double bestPriceRatio) {
        Quotation priceRange = candle.getHighPrice().subtract(candle.getLowPrice());

        return candle
            .getLowPrice()
            .add(priceRange.multiply(BigDecimal.valueOf(bestPriceRatio)));
    }

    protected void checkAccountAvailable(String accountId) throws AbstractException {
        AccountState accountState = mockBroker.userService.getAccountState(accountId);

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
        boolean isEnoughOfMoney = this.mockBroker.operationsService.isEnoughOfMoney(
            order.getAccountId(),
            Money.of(order.getInstrument().getCurrency(), order.getTotalPrice())
        );

        if (!isEnoughOfMoney) {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    protected void checkEnoughOfPositionToSell(Order order) throws AbstractException {
        Position position = mockBroker.operationsService.getPositionByInstrumentId(
            order.getAccountId(),
            order.getInstrument().getUid()
        );
        long balance = (position == null) ? 0 : position.getBalance();

        if (balance < order.getLotsRequested() * order.getInstrument().getLot()) {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    protected PostOrderResponse toServiceResponse(Order order) {
        String currency = order.getInstrument().getCurrency();
        Money commissionPriceMoney = Money.of(currency, order.getCommissionPrice());

        return new PostOrderResponse()
            .setOrderId(order.getOrderId())
            .setIdempotencyKey(order.getIdempotencyKey())
            .setDirection(order.getDirection())
            .setTotalPricePerOne(Money.of(currency, order.getTotalPricePerOne()))
            .setExecutedCommission(commissionPriceMoney)
            .setInitialCommission(commissionPriceMoney)
            .setInstrumentUid(order.getInstrument().getUid())
            .setOrderType(order.getOrderType())
            .setLotsExecuted(order.getLotsExecuted())
            .setLotsRequested(order.getLotsRequested())
            .setTotalPrice(Money.of(currency, order.getTotalPrice()))
            .setInitialPrice(Money.of(currency, order.getInitialPrice()))
            .setInitialPricePerOne(Money.of(currency, order.getInitialPricePerOne()))
            .setExecutionStatus(order.getExecutionStatus());
    }

    protected void calculateOrderPrices(Order order) {
        Instrument instrument = order.getInstrument();
        Quotation instrumentPrice = order.getInstrumentPrice();

        long quantity = order.getLotsRequested();
        int lotSize = instrument.getLot();

        Quotation initialPrice = instrumentPrice
            .multiply(BigDecimal.valueOf(lotSize))
            .multiply(BigDecimal.valueOf(quantity));
        Quotation commissionPrice = initialPrice.multiply(Quotation.of(commissionRatio));
        Quotation totalPrice = OrderDirection.BUY == order.getDirection()
            ? initialPrice.add(commissionPrice)
            : initialPrice.subtract(commissionPrice);
        Quotation totalPricePerOne = totalPrice.divide(BigDecimal.valueOf(quantity * lotSize));

        order
            .setCommissionPrice(commissionPrice)
            .setInitialPrice(initialPrice)
            .setInitialPricePerOne(instrumentPrice)
            .setTotalPrice(totalPrice)
            .setTotalPricePerOne(totalPricePerOne);
    }

    protected void registerOrder(Order order) throws AbstractException {
        if (order.getIdempotencyKey() == null) {
            order.setIdempotencyKey(UUID.randomUUID().toString());
        } else if (ordersRequestIds.contains(order.getIdempotencyKey())) {
            throw ExceptionBuilder.create(ErrorCode.DUPLICATE_ORDER);
        }

        ordersRequestIds.add(order.getIdempotencyKey());

        orders
            .computeIfAbsent(order.getAccountId(), x -> new HashMap<>())
            .put(order.getOrderId(), order);
    }
}
