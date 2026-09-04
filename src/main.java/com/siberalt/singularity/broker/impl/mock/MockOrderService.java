package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.position.Position;
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
import com.siberalt.singularity.entity.transaction.TransactionStatus;
import com.siberalt.singularity.entity.transaction.TransactionType;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

public class MockOrderService implements OrderService {
    public static final double DEFAULT_COMMISSION_RATIO = 0.003;

    protected Clock clock;
    protected MockOperationsService operationsService;
    protected MockInstrumentService instrumentService;
    protected MockMarketDataService marketDataService;
    protected MockUserService userService;
    protected double buyBestPriceRatio = 0.3;
    protected double sellBestPriceRatio = 0.7;
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected OrderRepository orderRepository;
    protected OperationRepository operationRepository;
    protected TransactionSpecProvider commissionTransactionSpecProvider = new CommissionTransactionSpecProvider(DEFAULT_COMMISSION_RATIO);
    private TransactionSpecProvider orderTransactionSpecProvider = new OrderTransactionSpecProvider();

    public MockOrderService(
        Clock clock,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockMarketDataService marketDataService,
        MockUserService userService,
        OrderRepository orderRepository,
        OperationRepository operationRepository
    ) {
        this.clock = clock;
        this.operationsService = operationsService;
        this.instrumentService = instrumentService;
        this.marketDataService = marketDataService;
        this.userService = userService;
        this.orderRepository = orderRepository;
        this.operationRepository = operationRepository;
    }

    public MockOrderService(
        Clock clock,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockMarketDataService marketDataService,
        MockUserService userService,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        TransactionSpecProvider commissionTransactionSpecProvider,
        TransactionSpecProvider orderTransactionSpecProvider
    ) {
        this(clock, operationsService, instrumentService, marketDataService, userService, orderRepository, operationRepository);
        this.commissionTransactionSpecProvider = commissionTransactionSpecProvider;
        this.orderTransactionSpecProvider = orderTransactionSpecProvider;
    }

    @Override
    public GetPriceResponse getPrice(GetPriceRequest request) throws AbstractException {
        validatePostOrderRequest(request.getPostOrderRequest());

        Order order = createOrder(request.getPostOrderRequest());
        calculateTransactions(order);

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

        cancel(cancelOrder);

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

    protected void cancel(Order order) {
        order
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.CANCELLED);
        operationRepository.save(toCancelOperation(order));
        orderRepository.delete(order);
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
        order
            .setExecutedTime(clock.currentTime())
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());

        registerOrder(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        checkTransactionsApplied(balance.applyTransactions(transactionSpecs));
        operationsService.subtractFromPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );

        registerOperations(order, transactionSpecs);

        return toServiceResponse(order);
    }

    protected void buyInstrument(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        order
            .setExecutedTime(clock.currentTime())
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());
        registerOrder(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        checkTransactionsApplied(balance.applyTransactions(transactionSpecs));
        operationsService.addToPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );

        registerOperations(order, transactionSpecs);
    }

    /**
     * A transaction can still be rejected by the balance itself (an overdraft the pre-checks did
     * not catch). Applying the rest of the fill - the position change, the operations - on top of a
     * rejected transaction would leave the account inconsistent, so fail the order instead.
     */
    protected void checkTransactionsApplied(List<Transaction> transactions) throws AbstractException {
        for (Transaction transaction : transactions) {
            if (transaction.getStatus() == TransactionStatus.FAILED) {
                throw ExceptionBuilder
                    .newBuilder(ErrorCode.INSUFFICIENT_BALANCE)
                    .withMessage(transaction.getErrorMessage())
                    .build();
            }
        }
    }

    protected void registerOperations(Order order, List<TransactionSpec> transactionSpecs) {
        for (TransactionSpec spec : transactionSpecs) {
            operationRepository.save(toOperation(order, spec, OperationState.EXECUTED));
        }
        orderRepository.delete(order);
    }

    protected Operation toOperation(Order order, TransactionSpec spec, OperationState state) {
        OperationType type = mapTransactionType(spec.type());
        boolean isTrade = type.isBuy() || type.isSell();

        return Operation.builder()
            .id(UUID.randomUUID().toString())
            .accountId(order.getAccountId())
            .instrumentUid(order.getInstrument().getUid())
            .direction(type)
            .quantity(isTrade ? order.getLotsRequested() : 0)
            .quantityDone(isTrade ? order.getLotsExecuted() : 0)
            .price(isTrade ? order.getInstrumentPrice() : null)
            .payment(spec.amount().getQuotation())
            .state(state)
            .date(order.getCreatedTime())
            .executedDate(order.getExecutedTime())
            .build();
    }

    protected Operation toCancelOperation(Order order) {
        OperationType type = order.getDirection().isBuy() ? OperationType.BUY : OperationType.SELL;

        return Operation.builder()
            .id(UUID.randomUUID().toString())
            .accountId(order.getAccountId())
            .instrumentUid(order.getInstrument().getUid())
            .direction(type)
            .quantity(order.getLotsRequested())
            .quantityDone(0)
            .price(order.getInstrumentPrice())
            .payment(Quotation.ZERO)
            .state(OperationState.CANCELED)
            .date(order.getCreatedTime())
            .executedDate(order.getExecutedTime())
            .build();
    }

    protected OperationType mapTransactionType(TransactionType type) {
        return switch (type) {
            case BUY -> OperationType.BUY;
            case SELL -> OperationType.SELL;
            case COMMISSION -> OperationType.BROKER_FEE;
            default -> OperationType.UNSPECIFIED;
        };
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

        Quotation instrumentPrice = calculateCurrentPrice(request.getOrderType(), request.getDirection(), currentCandle);

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

    protected Quotation calculateCurrentPrice(OrderType orderType, OrderDirection orderDirection, Candle currentCandle) {
        double bestPriceRatio = switch (orderDirection) {
            case BUY -> buyBestPriceRatio;
            case SELL -> sellBestPriceRatio;
            case UNSPECIFIED -> 1;
        };

        orderType = Objects.requireNonNullElse(orderType, OrderType.LIMIT);

        return switch (orderType) {
            case UNSPECIFIED, LIMIT, MARKET -> currentCandle.open();
            case BEST_PRICE -> calculateBestPrice(currentCandle, bestPriceRatio);
        };
    }

    protected Quotation calculateBestPrice(Candle candle, double bestPriceRatio) {
        Quotation priceRange = candle.high().subtract(candle.low());

        return candle
            .low()
            .add(priceRange.multiply(BigDecimal.valueOf(bestPriceRatio)));
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

    protected List<TransactionSpec> calculateTransactions(Order order) {
        Optional<TransactionSpec> commissionTransactionSpec = commissionTransactionSpecProvider.provide(order);
        Optional<TransactionSpec> orderTransactionSpec = orderTransactionSpecProvider.provide(order);

        List<TransactionSpec> transactionSpecs = new ArrayList<>();
        List<Quotation> balanceChanges = new ArrayList<>();

        orderTransactionSpec.ifPresent(
            spec -> {
                Quotation amount = spec.amount().getQuotation();
                balanceChanges.add(amount);
                order.setExecutedCommission(amount);
                transactionSpecs.add(spec);
            }
        );
        commissionTransactionSpec.ifPresent(
            spec -> {
                balanceChanges.add(spec.amount().getQuotation());
                transactionSpecs.add(spec);
            }
        );
        order.setBalanceChange(Quotation.sum(balanceChanges));

        return transactionSpecs;
    }

    protected void registerOrder(Order order) throws AbstractException {
        if (order.getIdempotencyKey() == null) {
            order.setIdempotencyKey(UUID.randomUUID().toString());
        } else {
            Order existingOrder = orderRepository.getByIdempotencyKey(order.getIdempotencyKey());

            // The same order may be re-registered under its own key (a limit order is stored once
            // when scheduled and again when it fills); only a different order reusing the key is a
            // duplicate.
            if (existingOrder != null && !existingOrder.getId().equals(order.getId())) {
                throw ExceptionBuilder.create(ErrorCode.DUPLICATE_ORDER);
            }
        }

        orderRepository.save(order);
    }
}
