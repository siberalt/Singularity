package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.exception.NotFoundException;
import com.siberalt.singularity.broker.contract.service.exception.UnimplementedException;
import com.siberalt.singularity.broker.contract.service.order.response.GetPriceResponse;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.position.Position;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.config.InstrumentConfig;
import com.siberalt.singularity.broker.impl.mock.config.MockBrokerConfig;
import com.siberalt.singularity.entity.operation.InMemoryOperationRepository;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.test.util.ConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link MockOrderService} behaviour that does not depend on which broker built it, run once per
 * broker by a subclass. Every test acts through the order service; the operations service only
 * funds the account beforehand and is read back afterwards to check what the order did to it.
 * <p>
 * The one thing the brokers do differently - whether an order the market is not ready for is parked
 * until the price arrives or refused outright - is tested by the subclasses themselves. The helpers
 * here therefore never guess the expected outcome: a test says {@code assertBuyFilled},
 * {@code assertBuyParked} or {@code assertBuyRefused}, and that is what gets checked.
 */
public abstract class AbstractMockOrderServiceTest {
    protected static final String SETTINGS_PATH = "src/test/resources/broker.mock/test-settings.yaml";
    protected MockBroker broker;
    protected MockBrokerConfig config;
    protected MockOrderService orderService;
    protected ReadCandleRepository candleStorage;
    protected Account testAccount;
    protected Instant currentTime;
    protected Quotation commissionRatio;
    protected ReadInstrumentRepository instrumentStorage;
    protected OrderRepository orderRepository;
    protected OperationRepository operationRepository;
    protected Clock clock;

    @BeforeEach
    public void setUp() throws Exception {
        config = ConfigLoader.load(MockBrokerConfig.class, SETTINGS_PATH);
        commissionRatio = Quotation.of(config.getOrderService().getCommissionRatio());

        Instrument instrument = createInstrument(config.getInstrument());
        currentTime = Instant.parse("2021-12-15T15:00:00Z");

        instrumentStorage = mock(ReadInstrumentRepository.class);
        when(instrumentStorage.get(MockBroker.DEFAULT_ID, instrument.getUid())).thenReturn(Optional.of(instrument));
        candleStorage = mock(ReadCandleRepository.class);
        orderRepository = new InMemoryOrderRepository();
        operationRepository = new InMemoryOperationRepository();

        clock = mock(Clock.class);
        when(clock.currentTime()).thenReturn(currentTime);

        broker = createBroker(candleStorage, instrumentStorage, orderRepository, operationRepository, clock);
        testAccount = broker.getUserService().openAccount(
            "testAccount",
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );

        orderService = broker.getOrderService();
    }

    abstract MockBroker createBroker(
        ReadCandleRepository candleStorage,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock
    );

    @Test
    public void testBuyWithInsufficientBalance() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        when(candleStorage.findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1))
            .thenReturn(List.of(testCandle));

        Money availableMoney = broker.getOperationsService()
            .getAvailableMoney(testAccount.getId(), config.getInstrument().getCurrency());

        if (availableMoney.isMoreThan(Money.of("RUB", Quotation.ZERO))) {
            broker.getOperationsService().subtractMoney(testAccount.getId(), availableMoney);
        }

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INSUFFICIENT_BALANCE,
            () -> postBuy(testCandle, OrderType.MARKET, 10, null)
        );
    }

    @Test
    public void getPriceReturnsCorrectTransactionsForValidRequest() throws AbstractException {
        PostOrderRequest postOrderRequest = new PostOrderRequest()
            .setInstrumentId(config.getInstrument().getUid())
            .setQuantity(10)
            .setAccountId(testAccount.getId())
            .setDirection(OrderDirection.BUY)
            .setOrderType(OrderType.MARKET);

        GetPriceRequest calculateRequest = new GetPriceRequest(postOrderRequest);

        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        when(candleStorage.findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1))
            .thenReturn(List.of(testCandle));
        when(clock.currentTime()).thenReturn(currentTime);

        GetPriceResponse response = orderService.getPrice(calculateRequest);

        assertNotEquals(Quotation.ZERO, response.executedCommission());
        Quotation expectedBalanceChange = testCandle.open()
            .multiply(10)
            .add(testCandle.open().multiply(10).multiply(commissionRatio))
            .multiply(-1);

        assertEquals(expectedBalanceChange, response.totalBalanceChange());
    }

    @Test
    public void getPriceThrowsExceptionForMissingInstrument() {
        assertThrowsWithErrorCode(
            NotFoundException.class,
            ErrorCode.INSTRUMENT_NOT_FOUND,
            () -> orderService.getPrice(getPriceRequest("invalidInstrumentId", testAccount.getId(), 10))
        );
    }

    @Test
    public void getPriceThrowsExceptionForMissingAccount() {
        assertThrowsWithErrorCode(
            NotFoundException.class,
            ErrorCode.ACCOUNT_NOT_FOUND,
            () -> orderService.getPrice(
                getPriceRequest(config.getInstrument().getUid(), "invalidAccountId", 10)
            )
        );
    }

    @Test
    public void getPriceThrowsExceptionForNegativeQuantity() {
        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.QUANTITY_MUST_BE_POSITIVE,
            () -> orderService.getPrice(
                getPriceRequest(config.getInstrument().getUid(), testAccount.getId(), -10)
            )
        );
    }

    @Test
    public void getPriceThrowsExceptionForZeroQuantity() {
        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.QUANTITY_MUST_BE_POSITIVE,
            () -> orderService.getPrice(
                getPriceRequest(config.getInstrument().getUid(), testAccount.getId(), 0)
            )
        );
    }

    /**
     * "Get orders" reports the ones still working, so an order that filled on the way in is not
     * among them.
     */
    @Test
    public void testGetReportsNoOrdersOnceTheyHaveFilled() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 11, 16, 10, 10, 100
        );

        addMoney(Quotation.of(1000));
        assertBuyFilled(testCandle, OrderType.MARKET, 1, testCandle.open());
        assertBuyFilled(testCandle, OrderType.LIMIT, 2, testCandle.open());

        GetOrdersResponse response = orderService.get(GetOrdersRequest.of(testAccount.getId()));

        assertTrue(response.getOrders().isEmpty());
    }

    /**
     * A fill journals a pair per order: the trade itself and the broker's fee for it. The two carry
     * different things - the trade carries the quantity and the price it went through at, the fee
     * carries only what it cost - so both are checked here.
     */
    @Test
    public void testFillJournalsATradeAndAFeePerOrder() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 11, 16, 10, 10, 100
        );

        addMoney(Quotation.of(1000));
        assertBuyFilled(testCandle, OrderType.MARKET, 1, testCandle.open());
        assertBuyFilled(testCandle, OrderType.MARKET, 2, testCandle.open());

        List<Operation> operations = operationRepository.getByAccountId(testAccount.getId(), TimeRange.MAX);
        List<Operation> tradeOperations = operations.stream()
            .filter(operation -> operation.direction().isBuy() || operation.direction().isSell())
            .toList();
        List<Operation> feeOperations = operations.stream()
            .filter(operation -> operation.direction() == OperationType.BROKER_FEE)
            .toList();

        // Two orders, nothing journalled beyond their trade/fee pairs.
        assertEquals(4, operations.size());
        assertEquals(2, tradeOperations.size());
        assertEquals(2, feeOperations.size());
        assertTrue(operations.stream().allMatch(operation -> operation.state() == OperationState.EXECUTED));

        assertTrue(tradeOperations.stream().allMatch(operation -> operation.direction().isBuy()));
        assertTrue(tradeOperations.stream().allMatch(operation -> testCandle.open().equals(operation.price())));
        assertEquals(
            List.of(1L, 2L),
            tradeOperations.stream().map(Operation::quantityDone).sorted().toList()
        );

        // A fee is not a trade: it has no quantity and no price of its own, only what was charged.
        assertTrue(feeOperations.stream().allMatch(operation -> operation.quantity() == 0));
        assertTrue(feeOperations.stream().allMatch(operation -> operation.quantityDone() == 0));
        assertTrue(feeOperations.stream().allMatch(operation -> operation.price() == null));

        // The fee is proportional to the traded value, so the two of them together come to what one
        // order for the combined quantity would have cost.
        assertEquals(
            expectedCommissionPayment(testCandle.open(), 3),
            Quotation.sum(feeOperations.stream().map(Operation::payment).toList())
        );
    }

    /**
     * With liquidity switched on, a bar can only give an order as much as it actually traded. What
     * the two brokers then do with the rest differs - one can wait for another bar and one cannot -
     * but the fill itself, and the fact that the order is not finished, is the same for both.
     */
    @Test
    public void testFillTakesOnlyWhatTheBarTraded() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);
        addMoney(Quotation.of(100000));
        // Whatever the broker does with the remainder, it looks for the end of the data first.
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L))).thenReturn(List.of(testCandle));

        PostOrderResponse response = postBuy(testCandle, OrderType.MARKET, 40, null);

        // A tenth of a bar that traded 100 lots is 10 of the 40 asked for.
        assertEquals(40, response.getLotsRequested());
        assertEquals(10, response.getLotsExecuted());
        assertEquals(ExecutionStatus.PARTIALLYFILL, response.getExecutionStatus());
        assertEquals(10, freePositionLots());

        // Not finished, so still one of the account's working orders.
        List<OrderState> activeOrders = orderService.get(GetOrdersRequest.of(testAccount.getId())).getOrders();

        assertEquals(1, activeOrders.size());
        assertEquals(response.getOrderId(), activeOrders.getFirst().getOrderId());
        assertEquals(10, activeOrders.getFirst().getLotsExecuted());
    }

    /**
     * Cancelling a partly filled order stops the rest of it without undoing what already traded -
     * the lots are bought and paid for, and no cancellation takes that back.
     */
    @Test
    public void testCancellingAPartlyFilledOrderKeepsWhatItAlreadyFilled() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);
        addMoney(Quotation.of(100000));
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L))).thenReturn(List.of(testCandle));

        PostOrderResponse response = postBuy(testCandle, OrderType.MARKET, 40, null);

        orderService.cancel(
            new CancelOrderRequest()
                .setOrderId(response.getOrderId())
                .setAccountId(testAccount.getId())
        );

        OrderState state = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(response.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertEquals(ExecutionStatus.CANCELLED, state.getExecutionStatus());
        assertEquals(10, state.getLotsExecuted());
        assertEquals(10, freePositionLots());
    }

    /**
     * An order that takes liquidity does not trade at the price the bar is quoted at - it crosses
     * the spread, and the account pays for it.
     */
    @Test
    public void testMarketFillCrossesTheSpread() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        orderService.getPriceModel().setHalfSpreadRatio(0.01);
        addMoney(Quotation.of(10000));

        Money moneyBefore = broker.getOperationsService()
            .getAvailableMoney(testAccount.getId(), config.getInstrument().getCurrency());

        PostOrderResponse response = postBuy(testCandle, OrderType.MARKET, 10, null);

        // The bar opens at 10, and a spread of two percent makes a buy pay 10.1 for it.
        Quotation fillPrice = Quotation.of(10.1);

        assertEquals(ExecutionStatus.FILL, response.getExecutionStatus());
        assertEquals(fillPrice, response.getInstrumentPrice().getQuotation());

        Quotation spent = fillPrice.multiply(10).add(expectedCommissionCost(fillPrice, 10));

        assertEquals(
            moneyBefore.subtract(Money.of(config.getInstrument().getCurrency(), spent)),
            broker.getOperationsService()
                .getAvailableMoney(testAccount.getId(), config.getInstrument().getCurrency())
        );
    }

    /**
     * A limit order is the passive side - others cross the spread to reach it, not the other way
     * round - so it gets the price it named however wide the spread is.
     */
    @Test
    public void testLimitFillDoesNotCrossTheSpread() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        orderService.getPriceModel().setHalfSpreadRatio(0.01);
        addMoney(Quotation.of(10000));

        PostOrderResponse response = postBuy(testCandle, OrderType.LIMIT, 10, Quotation.of(10));

        assertEquals(ExecutionStatus.FILL, response.getExecutionStatus());
        assertEquals(Quotation.of(10), response.getInstrumentPrice().getQuotation());
    }

    /**
     * What the commission costs the account - a positive amount, to be added to the price of the
     * lots when working out what an order came to in total.
     */
    protected Quotation expectedCommissionCost(Quotation instrumentPrice, long quantity) {
        return instrumentPrice
            .multiply(quantity)
            .multiply(config.getInstrument().getLot())
            .multiply(commissionRatio);
    }

    /**
     * The same commission as the journal records it: a balance change, and so negative. This is the
     * form to compare an operation's payment against, not the form to add up a cost with.
     */
    protected Quotation expectedCommissionPayment(Quotation instrumentPrice, long quantity) {
        return expectedCommissionCost(instrumentPrice, quantity).multiply(-1);
    }

    @Test
    public void testBuyMarket() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INSUFFICIENT_BALANCE,
            () -> postBuy(testCandle, OrderType.MARKET, 10, testCandle.open())
        );

        addMoney(testCandle.open().multiply(100));
        assertBuyFilled(testCandle, OrderType.MARKET, 12, testCandle.open());

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.QUANTITY_MUST_BE_POSITIVE,
            () -> postBuy(testCandle, OrderType.MARKET, -10, testCandle.open())
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.QUANTITY_MUST_BE_POSITIVE,
            () -> postBuy(testCandle, OrderType.MARKET, 0, null)
        );

        when(instrumentStorage.get(MockBroker.DEFAULT_ID, config.getInstrument().getUid()))
            .thenReturn(Optional.empty());

        assertThrowsWithErrorCode(
            NotFoundException.class,
            ErrorCode.INSTRUMENT_NOT_FOUND,
            () -> postBuy(testCandle, OrderType.MARKET, 10, testCandle.open())
        );

        verify(candleStorage, atLeastOnce()).findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1);
    }

    @Test
    public void testBuyLimitFillsWhenMarketMeetsThePrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.open().multiply(100));

        // A limit at the market price is fillable right away, whatever the broker does with the
        // ones that are not.
        assertBuyFilled(validCandle, OrderType.LIMIT, 10, validCandle.open());

        verify(candleStorage, atLeastOnce()).findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1);
    }

    @Test
    public void testBuyLimitRejectsInvalidPrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.open().multiply(100));

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INVALID_PARAMETER_PRICE,
            () -> postBuy(validCandle, OrderType.LIMIT, 10, Quotation.of(-100))
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INVALID_PARAMETER_PRICE,
            () -> postBuy(validCandle, OrderType.LIMIT, 10, Quotation.ZERO)
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.MISSING_PARAMETER_PRICE,
            () -> postBuy(validCandle, OrderType.LIMIT, 10, null)
        );
    }

    @Test
    public void testBuyBestPrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.open().multiply(100));

        assertBuyFilled(validCandle, OrderType.BEST_PRICE, 10, Quotation.of(5));
        verify(candleStorage, atLeastOnce()).findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1);
    }

    @Test
    public void testSellMarket() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Quotation openPrice = validCandle.open();

        addInstruments(100);
        assertSellFilled(validCandle, OrderType.MARKET, 15, openPrice);

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INSUFFICIENT_BALANCE,
            () -> postSell(validCandle, OrderType.MARKET, 1000, openPrice)
        );

        assertSellFilled(validCandle, OrderType.MARKET, 20, openPrice);
        verify(candleStorage, atLeastOnce()).findBeforeOrEqual(instrumentConfig.getUid(), currentTime, 1);
    }

    @Test
    public void testSellLimitFillsWhenMarketMeetsThePrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addInstruments(80);

        assertSellFilled(validCandle, OrderType.LIMIT, 10, validCandle.open());
        verify(candleStorage, atLeastOnce()).findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1);
    }

    @Test
    public void testSellBestPrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addInstruments(100);

        assertSellFilled(validCandle, OrderType.BEST_PRICE, 10, Quotation.of(11));
        verify(candleStorage, atLeastOnce()).findBeforeOrEqual(config.getInstrument().getUid(), currentTime, 1);
    }

    @Test
    public void testGetState() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.open().multiply(100));

        PostOrderResponse postResponse = assertBuyFilled(validCandle, OrderType.MARKET, 10, validCandle.open());

        // A filled order stays in OrderRepository, so getState() reports how it ended rather than
        // pretending it never existed.
        OrderState filledState = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(postResponse.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertEquals(ExecutionStatus.FILL, filledState.getExecutionStatus());
        assertEquals(postResponse.getOrderId(), filledState.getOrderId());
        assertEquals(postResponse.getLotsExecuted(), filledState.getLotsExecuted());

        Optional<Operation> tradeOperation = operationRepository.getByAccountId(testAccount.getId(), TimeRange.MAX)
            .stream()
            .filter(operation -> operation.state() == OperationState.EXECUTED)
            .filter(operation -> operation.direction().isBuy() || operation.direction().isSell())
            .findFirst();

        assertTrue(tradeOperation.isPresent());
        assertEquals(postResponse.getDirection().isBuy(), tradeOperation.get().direction().isBuy());
        assertEquals(postResponse.getInstrumentUid(), tradeOperation.get().instrumentUid());
        assertEquals(postResponse.getLotsRequested(), tradeOperation.get().quantity());
        assertEquals(postResponse.getLotsExecuted(), tradeOperation.get().quantityDone());

        assertThrowsWithErrorCode(
            NotFoundException.class,
            ErrorCode.ACCOUNT_NOT_FOUND,
            () -> orderService.getState(
                new GetOrderStateRequest()
                    .setOrderId(postResponse.getOrderId())
                    .setAccountId("invalidAccountId")
            )
        );
    }

    // region posting - arrange and act, no expectations

    protected PostOrderResponse postBuy(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        return orderService.post(orderRequest(priceCandle, orderType, quantity, priceLimit, OrderDirection.BUY));
    }

    protected PostOrderResponse postSell(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        return orderService.post(orderRequest(priceCandle, orderType, quantity, priceLimit, OrderDirection.SELL));
    }

    /**
     * Points the market data at {@code priceCandle} and moves the clock to it, then builds the
     * matching request.
     */
    protected PostOrderRequest orderRequest(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit,
        OrderDirection direction
    ) {
        InstrumentConfig instrumentConfig = config.getInstrument();

        when(candleStorage.findBeforeOrEqual(instrumentConfig.getUid(), priceCandle.getTime(), 1))
            .thenReturn(List.of(priceCandle));
        when(clock.currentTime()).thenReturn(priceCandle.getTime());

        return new PostOrderRequest()
            .setInstrumentId(instrumentConfig.getUid())
            .setQuantity(quantity)
            .setPrice(priceLimit)
            .setAccountId(testAccount.getId())
            .setDirection(direction)
            .setOrderType(orderType);
    }

    protected GetPriceRequest getPriceRequest(String instrumentId, String accountId, long quantity) {
        return new GetPriceRequest(
            new PostOrderRequest()
                .setInstrumentId(instrumentId)
                .setQuantity(quantity)
                .setAccountId(accountId)
                .setDirection(OrderDirection.BUY)
                .setOrderType(OrderType.MARKET)
        );
    }

    // endregion

    // region expectations - one outcome each

    /**
     * The order fills straight away: the money leaves the account and the order comes back FILL.
     */
    protected PostOrderResponse assertBuyFilled(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        MockOperationsService operationsService = broker.getOperationsService();

        Money moneyBefore = operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency());
        Quotation instrumentPrice = priceCandle.open();

        PostOrderResponse response = postBuy(priceCandle, orderType, quantity, priceLimit);

        assertOrderAccepted(response, OrderDirection.BUY, instrumentPrice);
        assertEquals(quantity, response.getLotsRequested());
        assertEquals(quantity, response.getLotsExecuted());
        assertEquals(ExecutionStatus.FILL, response.getExecutionStatus());

        Quotation totalPrice = instrumentPrice
            .add(instrumentPrice.multiply(commissionRatio))
            .multiply(quantity);

        assertEquals(
            moneyBefore.subtract(Money.of(instrumentConfig.getCurrency(), totalPrice)),
            operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
        );

        return response;
    }

    protected PostOrderResponse assertSellFilled(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        MockOperationsService operationsService = broker.getOperationsService();

        Money moneyBefore = operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency());
        long lotsBefore = freePositionLots();
        Quotation instrumentPrice = priceCandle.open();

        PostOrderResponse response = postSell(priceCandle, orderType, quantity, priceLimit);

        assertOrderAccepted(response, OrderDirection.SELL, instrumentPrice);
        assertEquals(quantity, response.getLotsRequested());
        assertEquals(quantity, response.getLotsExecuted());
        assertEquals(ExecutionStatus.FILL, response.getExecutionStatus());

        Quotation totalBalanceChange = instrumentPrice
            .subtract(instrumentPrice.multiply(commissionRatio))
            .multiply(quantity);

        assertEquals(totalBalanceChange, response.getTotalBalanceChange().getQuotation());
        assertEquals(lotsBefore - quantity, freePositionLots());
        assertEquals(
            moneyBefore.add(Money.of(instrumentConfig.getCurrency(), totalBalanceChange)),
            operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
        );

        return response;
    }

    /**
     * The order waits for the market: it comes back NEW, nothing is charged yet, and what it will
     * need is held back so a second order cannot be promised the same funds.
     */
    protected PostOrderResponse assertBuyParked(
        Candle priceCandle,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        MockOperationsService operationsService = broker.getOperationsService();

        Money moneyBefore = operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency());
        long lotsBefore = freePositionLots();
        Quotation instrumentPrice = priceCandle.open();

        PostOrderResponse response = postBuy(priceCandle, OrderType.LIMIT, quantity, priceLimit);

        assertOrderAccepted(response, OrderDirection.BUY, instrumentPrice);
        assertParkedResponse(response, quantity);

        // Reserved at the current market price, which is above the limit the order will fill at.
        Quotation reservedMoney = instrumentPrice
            .add(instrumentPrice.multiply(commissionRatio))
            .multiply(quantity)
            .multiply(instrumentConfig.getLot());

        assertEquals(
            moneyBefore.subtract(Money.of(instrumentConfig.getCurrency(), reservedMoney)),
            operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
        );
        assertEquals(lotsBefore, freePositionLots());

        return response;
    }

    protected PostOrderResponse assertSellParked(
        Candle priceCandle,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        MockOperationsService operationsService = broker.getOperationsService();

        Money moneyBefore = operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency());
        long lotsBefore = freePositionLots();
        long blockedBefore = blockedPositionLots();

        PostOrderResponse response = postSell(priceCandle, OrderType.LIMIT, quantity, priceLimit);

        assertOrderAccepted(response, OrderDirection.SELL, priceCandle.open());
        assertParkedResponse(response, quantity);

        long reservedLots = quantity * instrumentConfig.getLot();

        assertEquals(
            moneyBefore,
            operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
        );
        assertEquals(lotsBefore - reservedLots, freePositionLots());
        assertEquals(blockedBefore + reservedLots, blockedPositionLots());

        return response;
    }

    /**
     * The broker will not take an order it cannot fill now.
     */
    protected void assertBuyRefused(Candle priceCandle, long quantity, Quotation priceLimit) {
        assertThrowsWithErrorCode(
            UnimplementedException.class,
            ErrorCode.UNIMPLEMENTED,
            () -> postBuy(priceCandle, OrderType.LIMIT, quantity, priceLimit)
        );
    }

    protected void assertSellRefused(Candle priceCandle, long quantity, Quotation priceLimit) {
        assertThrowsWithErrorCode(
            UnimplementedException.class,
            ErrorCode.UNIMPLEMENTED,
            () -> postSell(priceCandle, OrderType.LIMIT, quantity, priceLimit)
        );
    }

    protected void assertOrderAccepted(
        PostOrderResponse response,
        OrderDirection direction,
        Quotation instrumentPrice
    ) {
        assertNotNull(response.getOrderId());
        assertEquals(direction, response.getDirection());
        assertEquals(config.getInstrument().getUid(), response.getInstrumentUid());
        assertEquals(instrumentPrice, response.getInstrumentPrice().getQuotation());
    }

    protected void assertParkedResponse(PostOrderResponse response, long quantity) {
        assertEquals(quantity, response.getLotsRequested());
        assertEquals(0, response.getLotsExecuted());
        assertEquals(Quotation.ZERO, response.getTotalBalanceChange().getQuotation());
        assertEquals(ExecutionStatus.NEW, response.getExecutionStatus());
    }

    // endregion

    protected long freePositionLots() throws AbstractException {
        Position position = broker.getOperationsService()
            .getPositionByInstrumentId(testAccount.getId(), config.getInstrument().getUid());

        return (position != null) ? position.getBalance() : 0;
    }

    protected long blockedPositionLots() throws AbstractException {
        Position position = broker.getOperationsService()
            .getPositionByInstrumentId(testAccount.getId(), config.getInstrument().getUid());

        return (position != null) ? position.getBlocked() : 0;
    }

    protected void addInstruments(long count) throws AbstractException {
        broker.getOperationsService().addToPosition(
            testAccount.getId(),
            config.getInstrument().getUid(),
            count
        );
    }

    protected void addMoney(Quotation amount) throws AbstractException {
        addMoney(config.getInstrument().getCurrency(), amount);
    }

    protected void addMoney(String currency, Quotation amount) throws AbstractException {
        broker.getOperationsService().addMoney(testAccount.getId(), Money.of(currency, amount));
    }

    protected Instrument createInstrument(InstrumentConfig config) {
        return new Instrument()
            .setCurrency(config.getCurrency())
            .setLot(config.getLot())
            .setInstrumentType(config.getInstrumentType())
            .setUid(config.getUid());
    }

    protected Candle createCandle(
        Instant time,
        int closePrice,
        int highPrice,
        int lowPrice,
        int openPrice,
        int volume
    ) {
        var instrumentConfig = config.getInstrument();

        return new Candle(
            instrumentConfig.getUid(),
            new TimePoint(time),
            Quotation.of(openPrice),
            Quotation.of(closePrice),
            Quotation.of(highPrice),
            Quotation.of(lowPrice),
            volume
        );
    }

    protected <T extends AbstractException> void assertThrowsWithErrorCode(
        Class<T> exceptionClass,
        ErrorCode errorCode,
        Executable executable
    ) {
        T exception = assertThrows(exceptionClass, executable);
        assertEquals(errorCode, exception.getErrorCode());
    }
}
