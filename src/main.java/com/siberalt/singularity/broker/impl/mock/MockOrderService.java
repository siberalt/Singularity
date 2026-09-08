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
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Duration;
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
    protected SimulationMarketData marketDataService;
    protected MockUserService userService;
    protected OrderRepository orderRepository;
    protected OrderRegistry orderRegistry;
    protected OrderPriceModel priceModel;
    protected LiquidityModel liquidityModel;
    protected OrderExecutor orderExecutor;
    protected PendingOrderHandler pendingOrderHandler;
    protected Duration executionLatency = Duration.ZERO;

    public MockOrderService(
        Clock clock,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        SimulationMarketData marketDataService,
        MockUserService userService,
        OrderRepository orderRepository,
        OrderRegistry orderRegistry,
        OrderPriceModel priceModel,
        LiquidityModel liquidityModel,
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
        this.liquidityModel = liquidityModel;
        this.orderExecutor = orderExecutor;
        this.pendingOrderHandler = pendingOrderHandler;
    }

    /**
     * How much of an order the market is assumed able to absorb. Infinite by default - switch it off
     * to have large orders fill across several bars instead of all at one price.
     */
    public LiquidityModel getLiquidityModel() {
        return liquidityModel;
    }

    @Override
    public GetPriceResponse getPrice(GetPriceRequest request) throws AbstractException {
        validatePostOrderRequest(request.getPostOrderRequest());

        Order order = createOrder(request.getPostOrderRequest());
        FillQuote quote = orderExecutor.quote(order, order.getLotsRequested());

        return new GetPriceResponse(quote.balanceChange(), quote.commission());
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        validatePostOrderRequest(request);

        if (request.getDirection() == OrderDirection.UNSPECIFIED) {
            throw ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_DIRECTION);
        }

        return fill(request);
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        checkAccountAvailable(request.getAccountId());

        Order cancelOrder = orderRepository.getByAccountIdAndOrderId(request.getAccountId(), request.getOrderId());

        if (null == cancelOrder) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        // A partially filled order is still working, so it can still be pulled - what already
        // traded stays traded, and OrderRegistry.cancel keeps that in the journal.
        if (!isActive(cancelOrder.getExecutionStatus())) {
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

    /**
     * What a fill is priced at - the spread it crosses, its own market impact, and where inside a
     * bar a best-price order is assumed to land. All of it off or neutral by default.
     */
    public OrderPriceModel getPriceModel() {
        return priceModel;
    }

    public Duration getExecutionLatency() {
        return executionLatency;
    }

    /**
     * How long an order takes to reach the exchange. Zero by default, which lets an order trade
     * against the very bar it was placed in - convenient, and the single most flattering assumption
     * a backtest can make, since a strategy reacting to a bar could not in reality have traded
     * inside it.
     * <p>
     * Anything above zero means no order fills synchronously any more: {@code post} comes back NEW
     * and the fill arrives later, so a caller reading lots executed straight off the response will
     * see none. Only a broker that advances time can honour it - a plain one refuses. Cancellation
     * is not delayed; only the order's own arrival is modelled.
     */
    public MockOrderService setExecutionLatency(Duration executionLatency) {
        if (executionLatency.isNegative()) {
            throw new IllegalArgumentException("Execution latency cannot be negative, got " + executionLatency);
        }

        this.executionLatency = executionLatency;
        return this;
    }

    protected Candle requireCurrentCandle(String instrumentUid) throws AbstractException {
        Candle currentCandle = marketDataService.currentCandle(instrumentUid);

        if (currentCandle == null) {
            throw new MockBrokerException("Candle not found");
        }

        return currentCandle;
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

    /**
     * Buying and selling differ in three places and nowhere else - what the account has to have
     * enough of, which side of the limit price the market must be on, and which way the instrument
     * moves - so they are one method with three forks rather than two that drift apart.
     * <p>
     * The order of the steps carries weight. What the account can cover is checked before any of
     * the bar is claimed: an order refused here must not have taken a share of the bar with it on
     * the way out. And the fill is priced only once it is going ahead, since the price a fill goes
     * through at is a fact about a fill that happens.
     */
    protected PostOrderResponse fill(PostOrderRequest request) throws AbstractException {
        Order order = createOrder(request);

        checkAccountCanCover(order);

        long lots = reachesMarketNow() && marketMeetsPrice(order) ? takeLiquidity(order) : 0;

        if (lots == 0) {
            park(order);

            return toServiceResponse(order);
        }

        priceFill(order, lots);
        orderExecutor.fill(order, lots, orderExecutor.quote(order, lots));
        handleRemainder(order);

        return toServiceResponse(order);
    }

    protected void checkAccountCanCover(Order order) throws AbstractException {
        if (order.getDirection() == OrderDirection.BUY) {
            checkEnoughOfMoneyToBuy(order, orderExecutor.quote(order, order.getLotsRequested()));

            return;
        }

        checkEnoughOfPositionToSell(order);
    }

    /**
     * Whether an order placed now is at the exchange now. With a latency configured it is not, and
     * nothing can fill against the bar it was placed in - the market it is reacting to has already
     * moved on by the time the order arrives.
     */
    protected boolean reachesMarketNow() {
        return executionLatency.isZero();
    }

    /**
     * Hands an order that is not filling now to the pending handler, together with the moment it
     * may start trading from. With no latency that moment is now - the order is at the exchange
     * already and only the price is missing; with one it is when the order gets there.
     */
    protected void park(Order order) throws AbstractException {
        pendingOrderHandler.onPending(order, clock.currentTime().plus(executionLatency));
    }

    /**
     * Moves the order from the price the instrument is quoted at to the price this fill actually
     * goes through at - the spread it has to cross, plus the impact of its own size.
     * <p>
     * Called only for a fill that is going ahead. An order left waiting keeps the quoted price
     * instead: that is what its limit is compared against and what its reservation is sized by, and
     * there is no fill yet to charge for.
     */
    protected void priceFill(Order order, long lots) throws AbstractException {
        Candle currentCandle = requireCurrentCandle(order.getInstrument().getUid());

        order.setInstrumentPrice(priceModel.fillPrice(order, currentCandle, lots));
    }

    /**
     * Takes this order's share out of the current bar's budget, so the orders filling after it on
     * the same bar see what is left rather than the whole of it again. Zero means the bar has
     * nothing to give - either it traded too little to begin with, or the orders ahead took the
     * lot - which for these purposes is the same situation as the price not being met: the order
     * has to wait either way.
     * <p>
     * Nothing that follows can refuse the order, so nothing has to be handed back. That is why the
     * account is checked first.
     */
    protected long takeLiquidity(Order order) throws AbstractException {
        String instrumentUid = order.getInstrument().getUid();

        return liquidityModel.take(
            instrumentUid,
            requireCurrentCandle(instrumentUid),
            order.getLotsRequested() - order.getLotsExecuted()
        );
    }

    /**
     * A fill that could not take the whole order leaves the rest of it working, and what that means
     * is the pending handler's business - a broker that cannot advance time simply leaves it as it
     * is.
     */
    protected void handleRemainder(Order order) throws AbstractException {
        if (order.getLotsExecuted() < order.getLotsRequested()) {
            pendingOrderHandler.onPartiallyFilled(order);
        }
    }

    /**
     * Whether the market is on the right side of the order's price. Only a limit order names one -
     * anything else takes what the market offers - and it is the mirror of itself by direction: a
     * buy will not pay more than its limit, a sell will not accept less.
     */
    protected boolean marketMeetsPrice(Order order) {
        if (OrderType.LIMIT != order.getOrderType()) {
            return true;
        }

        Quotation priceLimit = order.getRequestedPrice();

        return order.getDirection() == OrderDirection.BUY
            ? priceLimit.isGreaterOrEqual(order.getInstrumentPrice())
            : priceLimit.isLessOrEqual(order.getInstrumentPrice());
    }

    protected Order createOrder(PostOrderRequest request) throws AbstractException {
        Instrument instrument = instrumentService
            .get(GetRequest.of(request.getInstrumentId()))
            .getInstrument();

        if (instrument == null) {
            throw ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND);
        }

        Candle currentCandle = requireCurrentCandle(request.getInstrumentId());

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

    /**
     * Checked against the whole order, not just the part that can fill now: an order the account
     * cannot afford in full is refused outright rather than filled part way and left working with
     * nothing to pay for the rest.
     */
    protected void checkEnoughOfMoneyToBuy(Order order, FillQuote quote) throws AbstractException {
        boolean isEnoughOfMoney = operationsService.isEnoughOfMoney(
            order.getAccountId(),
            Money.of(order.getInstrument().getCurrency(), quote.cost())
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
