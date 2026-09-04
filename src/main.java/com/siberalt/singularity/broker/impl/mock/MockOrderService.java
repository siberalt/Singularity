package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.entity.position.Position;
import com.siberalt.singularity.broker.contract.service.order.*;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.*;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.user.AccountState;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.*;

/**
 * The mock broker's order service. It validates a request, turns it into a priced {@link Order},
 * and then either hands the fill to an {@link OrderExecutor} or, when the order cannot fill at the
 * current price, to a {@link PendingOrderHandler}. That last collaborator is the whole difference
 * between a plain mock broker and an event-simulated one, so there is a single implementation of
 * this class rather than a base class and a subclass overriding half of it.
 */
public class MockOrderService implements OrderService {
    public static final double DEFAULT_COMMISSION_RATIO = DefaultOrderExecutor.DEFAULT_COMMISSION_RATIO;

    protected Clock clock;
    protected MockOperationsService operationsService;
    protected MockInstrumentService instrumentService;
    protected MockMarketDataService marketDataService;
    protected MockUserService userService;
    protected OrderRepository orderRepository;
    protected OrderRegistry orderRegistry;
    protected OrderPriceModel priceModel;
    protected OrderExecutor orderExecutor;
    protected PendingOrderHandler pendingOrderHandler;

    public MockOrderService(
        Clock clock,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockMarketDataService marketDataService,
        MockUserService userService,
        OrderRepository orderRepository,
        OrderRegistry orderRegistry,
        OrderPriceModel priceModel,
        OrderExecutor orderExecutor,
        PendingOrderHandler pendingOrderHandler
    ) {
        this.clock = clock;
        this.operationsService = operationsService;
        this.instrumentService = instrumentService;
        this.marketDataService = marketDataService;
        this.userService = userService;
        this.orderRepository = orderRepository;
        this.orderRegistry = orderRegistry;
        this.priceModel = priceModel;
        this.orderExecutor = orderExecutor;
        this.pendingOrderHandler = pendingOrderHandler;
    }

    @Override
    public GetPriceResponse getPrice(GetPriceRequest request) throws AbstractException {
        validatePostOrderRequest(request.getPostOrderRequest());

        Order order = createOrder(request.getPostOrderRequest());
        orderExecutor.calculateTransactions(order);

        return new GetPriceResponse(
            order.getBalanceChange(),
            order.getExecutedCommission()
        );
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        validatePostOrderRequest(request);

        return switch (request.getDirection()) {
            case BUY -> buy(request);
            case SELL -> sell(request);
            case UNSPECIFIED -> throw ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_DIRECTION);
        };
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        Order cancelOrder = orderRepository.getByAccountIdAndOrderId(request.getAccountId(), request.getOrderId());

        if (null == cancelOrder) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        if (cancelOrder.getExecutionStatus() != ExecutionStatus.NEW) {
            throw ExceptionBuilder.create(ErrorCode.CANCEL_ORDER_ERROR);
        }

        orderRegistry.cancel(cancelOrder);
        pendingOrderHandler.onCancelled(cancelOrder);

        return new CancelOrderResponse().setTime(clock.currentTime());
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        if (request.getOrderId() == null) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_ORDER_ID);
        }

        Order order = orderRepository.getByAccountIdAndOrderId(request.getAccountId(), request.getOrderId());

        if (order == null) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        if (request.getPriceType() != null && order.getPriceType() != request.getPriceType()) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        return order.getState();
    }

    /**
     * Active orders only. The repository keeps every order the account ever placed so that
     * {@link #getState} can still report a finished one, so filled, cancelled and rejected orders
     * are filtered out here - "get orders" means the ones still working.
     */
    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        List<OrderState> accountOrders = orderRepository
            .getByAccountId(request.getAccountId())
            .stream()
            .filter(order -> isActive(order.getExecutionStatus()))
            .map(Order::getState)
            .toList();

        return new GetOrdersResponse().setOrders(accountOrders);
    }

    protected boolean isActive(ExecutionStatus executionStatus) {
        return executionStatus == ExecutionStatus.NEW || executionStatus == ExecutionStatus.PARTIALLYFILL;
    }

    public PendingOrderHandler getPendingOrderHandler() {
        return pendingOrderHandler;
    }

    public double getBuyBestPriceRatio() {
        return priceModel.getBuyBestPriceRatio();
    }

    public MockOrderService setBuyBestPriceRatio(double buyBestPriceRatio) {
        priceModel.setBuyBestPriceRatio(buyBestPriceRatio);
        return this;
    }

    public double getSellBestPriceRatio() {
        return priceModel.getSellBestPriceRatio();
    }

    public MockOrderService setSellBestPriceRatio(double sellBestPriceRatio) {
        priceModel.setSellBestPriceRatio(sellBestPriceRatio);
        return this;
    }

    protected void validatePostOrderRequest(PostOrderRequest request) throws AbstractException {
        if (request.getAccountId() == null || request.getAccountId().isEmpty()) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_ACCOUNT_ID);
        }

        checkAccountAvailable(request.getAccountId());

        if (request.getInstrumentId() == null || request.getInstrumentId().isEmpty()) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_INSTRUMENT_ID);
        }

        if (request.getDirection() == null) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_DIRECTION);
        }

        if (request.getOrderType() == null) {
            throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_ORDER_TYPE);
        }

        if (request.getQuantity() <= 0) {
            throw ExceptionBuilder.create(ErrorCode.QUANTITY_MUST_BE_POSITIVE);
        }

        if (request.getOrderType().equals(OrderType.LIMIT)) {
            if (request.getPrice() == null) {
                throw ExceptionBuilder.create(ErrorCode.MISSING_PARAMETER_PRICE);
            }

            if (request.getPrice().isLessOrEqual(Quotation.ZERO)) {
                throw ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_PRICE);
            }
        }
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);
        List<TransactionSpec> transactionSpecs = orderExecutor.calculateTransactions(order);
        checkEnoughOfMoneyToBuy(order);

        if (canBuyNow(order)) {
            orderExecutor.buy(order, transactionSpecs);
        } else {
            pendingOrderHandler.onNotFillable(order);
        }

        return toServiceResponse(order);
    }

    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);
        List<TransactionSpec> transactionSpecs = orderExecutor.calculateTransactions(order);
        checkEnoughOfPositionToSell(order);

        if (canSellNow(order)) {
            orderExecutor.sell(order, transactionSpecs);
        } else {
            pendingOrderHandler.onNotFillable(order);
        }

        return toServiceResponse(order);
    }

    protected boolean canBuyNow(Order order) {
        Quotation priceLimit = order.getRequestedPrice();

        return OrderType.LIMIT != order.getOrderType() || priceLimit.isGreaterOrEqual(order.getInstrumentPrice());
    }

    protected boolean canSellNow(Order order) {
        Quotation priceLimit = order.getRequestedPrice();

        return OrderType.LIMIT != order.getOrderType() || priceLimit.isLessOrEqual(order.getInstrumentPrice());
    }

    protected Order createOrder(PostOrderRequest request) throws AbstractException {
        Instrument instrument = instrumentService
            .get(GetRequest.of(request.getInstrumentId()))
            .getInstrument();

        if (instrument == null) {
            throw ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND);
        }

        Candle currentCandle = marketDataService.getInstrumentCurrentCandle(request.getInstrumentId());
        if (currentCandle == null) {
            throw new MockBrokerException("Candle not found");
        }

        Quotation instrumentPrice = priceModel.currentPrice(
            request.getOrderType(),
            request.getDirection(),
            currentCandle
        );

        return new Order()
            .setId(UUID.randomUUID().toString())
            .setIdempotencyKey(request.getIdempotencyKey())
            .setRequestedPrice(request.getPrice())
            .setCreatedTime(clock.currentTime())
            .setLotsRequested(request.getQuantity())
            .setAccountId(request.getAccountId())
            .setDirection(request.getDirection())
            .setOrderType(request.getOrderType())
            .setInstrument(instrument)
            .setInstrumentPrice(instrumentPrice);
    }

    protected void checkAccountAvailable(String accountId) throws AbstractException {
        AccountState accountState = userService.getAccountState(accountId);

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
        boolean isEnoughOfMoney = operationsService.isEnoughOfMoney(
            order.getAccountId(),
            Money.of(order.getInstrument().getCurrency(), order.getBalanceChange().multiply(-1))
        );

        if (!isEnoughOfMoney) {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    protected void checkEnoughOfPositionToSell(Order order) throws AbstractException {
        Position position = operationsService.getPositionByInstrumentId(
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

        return new PostOrderResponse()
            .setOrderId(order.getId())
            .setIdempotencyKey(order.getIdempotencyKey())
            .setDirection(order.getDirection())
            .setExecutedCommission(Money.of(currency, order.getExecutedCommission()))
            .setInstrumentUid(order.getInstrument().getUid())
            .setOrderType(order.getOrderType())
            .setLotsExecuted(order.getLotsExecuted())
            .setLotsRequested(order.getLotsRequested())
            .setTotalBalanceChange(Money.of(currency, order.getBalanceChange()))
            .setInstrumentPrice(Money.of(currency, order.getInstrumentPrice()))
            .setExecutionStatus(order.getExecutionStatus());
    }
}
