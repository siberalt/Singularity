package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.service.order.*;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.*;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.operation.AccountBalance;
import com.siberalt.singularity.broker.impl.mock.shared.user.AccountState;
import com.siberalt.singularity.entity.transaction.Transaction;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

public class MockOrderService implements OrderService {
    protected MockBroker mockBroker;
    protected double buyBestPriceRatio = 0.3;
    protected double sellBestPriceRatio = 0.7;
    protected double commissionRatio = 0.003;
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected OrderRepository orderRepository;
    protected CommissionTransactionSpecProvider commissionTransactionSpecProvider = new CommissionTransactionSpecProvider(commissionRatio);
    protected TransactionService transactionService = new TransactionService()
        .addProvider(new OrderTransactionSpecProvider())
        .addProvider(commissionTransactionSpecProvider);

    public MockOrderService(MockBroker mockBroker, OrderRepository orderRepository) {
        this.mockBroker = mockBroker;
        this.orderRepository = orderRepository;
    }

    @Override
    public CalculateResponse calculate(CalculateRequest request) throws AbstractException {
        validatePostOrderRequest(request.getPostOrderRequest());

        Order order = createOrder(request.getPostOrderRequest());
        List<TransactionSpec> transactionSpecs = calculateTransactions(order);

        return new CalculateResponse(
            order.getInstrument().getUid(),
            order.getBalanceChange(),
            order.getLotsRequested(),
            transactionSpecs
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

        cancel(cancelOrder);

        return new CancelOrderResponse().setTime(mockBroker.clock.currentTime());
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

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        List<OrderState> accountOrders = orderRepository
            .getByAccountId(request.getAccountId())
            .stream()
            .map(Order::getState)
            .toList();

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
        if (commissionRatio < 0 || commissionRatio > 1) {
            throw new IllegalArgumentException("Commission ratio must be between 0 and 1");
        }

        commissionTransactionSpecProvider.setCommissionRatio(commissionRatio);
        this.commissionRatio = commissionRatio;
    }

    protected void cancel(Order order) {
        order
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.CANCELLED);
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
        List<TransactionSpec> transactionSpecs = calculateTransactions(order);
        checkEnoughOfMoneyToBuy(order);

        if (!canBuyNow(order)) {
            throw ExceptionBuilder
                .newBuilder(ErrorCode.UNIMPLEMENTED)
                .withMessage("Limit orders are not implemented yet")
                .build();
        }

        buyInstrument(order, transactionSpecs);

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

    protected PostOrderResponse sellInstrument(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        MockOperationsService operationsService = mockBroker.operationsService;

        order
            .setExecutedTime(mockBroker.clock.currentTime())
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());

        registerOrder(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        List<Transaction> transactions = balance.applyTransactions(transactionSpecs);
        operationsService.subtractFromPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );
        order.setTransactions(transactions);

        return toServiceResponse(order);
    }

    protected void buyInstrument(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        MockOperationsService operationsService = mockBroker.operationsService;
        order
            .setExecutedTime(mockBroker.clock.currentTime())
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());
        registerOrder(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        List<Transaction> transactions = balance.applyTransactions(transactionSpecs);
        operationsService.addToPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );
        order.setTransactions(transactions);
    }

    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);
        List<TransactionSpec> transactionSpecs = calculateTransactions(order);
        checkEnoughOfPositionToSell(order);

        if (!canSellNow(order)) {
            throw ExceptionBuilder
                .newBuilder(ErrorCode.UNIMPLEMENTED)
                .withMessage("Limit orders are not implemented yet")
                .build();
        }

        return sellInstrument(order, transactionSpecs);
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
            .setId(UUID.randomUUID().toString())
            .setRequestedPrice(request.getPrice())
            .setCreatedTime(mockBroker.clock.currentTime())
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
            Money.of(order.getInstrument().getCurrency(), order.getBalanceChange())
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

        return new PostOrderResponse()
            .setOrderId(order.getId())
            .setIdempotencyKey(order.getIdempotencyKey())
            .setDirection(order.getDirection())
            .setTransactions(order.getTransactions())
            .setInstrumentUid(order.getInstrument().getUid())
            .setOrderType(order.getOrderType())
            .setLotsExecuted(order.getLotsExecuted())
            .setLotsRequested(order.getLotsRequested())
            .setTotalBalanceChange(Money.of(currency, order.getBalanceChange()))
            .setInstrumentPrice(Money.of(currency, order.getInstrumentPrice()))
            .setExecutionStatus(order.getExecutionStatus());
    }

    protected List<TransactionSpec> calculateTransactions(Order order) {
        List<TransactionSpec> transactionSpecs = transactionService.calculateSpecs(order);

        Quotation balanceChange = transactionService.sumSpecs(transactionSpecs);
        order.setBalanceChange(balanceChange);

        return transactionSpecs;
    }

    protected void registerOrder(Order order) throws AbstractException {
        if (order.getIdempotencyKey() == null) {
            order.setIdempotencyKey(UUID.randomUUID().toString());
        } else if (null == orderRepository.getByIdempotencyKey(order.getIdempotencyKey())) {
            throw ExceptionBuilder.create(ErrorCode.DUPLICATE_ORDER);
        }

        orderRepository.save(order);
    }
}
