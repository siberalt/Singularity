package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.container.simulation.SimulationBrokerContainer;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.exception.NotFoundException;
import com.siberalt.singularity.broker.contract.service.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.config.InstrumentConfig;
import com.siberalt.singularity.broker.impl.mock.config.MockBrokerConfig;
import com.siberalt.singularity.scheduler.SchedulerInterface;
import com.siberalt.singularity.simulation.shared.instrument.InstrumentStorageInterface;
import com.siberalt.singularity.simulation.shared.market.candle.Candle;
import com.siberalt.singularity.simulation.shared.market.candle.CandleStorageInterface;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.strategy.simulation.SimulationContext;
import com.siberalt.singularity.test.util.ConfigLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public abstract class MockOrderServiceTest {
    protected static final String SETTINGS_PATH = "src/test/resources/broker.mock/test-settings.yaml";
    protected MockBroker broker;
    protected MockBrokerConfig config;
    protected MockOrderService orderService;
    protected CandleStorageInterface candleStorage;
    protected Account testAccount;
    protected Instant currentTime;
    protected Quotation commissionRatio;
    protected InstrumentStorageInterface instrumentStorage;
    protected Clock clock;

    @BeforeEach
    public void setUp() throws Exception {
        config = ConfigLoader.load(MockBrokerConfig.class, SETTINGS_PATH);
        commissionRatio = Quotation.of(config.getOrderService().getCommissionRatio());

        Instrument instrument = createInstrument(config.getInstrument());
        currentTime = Instant.parse("2021-12-15T15:00:00Z");

        instrumentStorage = mock(InstrumentStorageInterface.class);
        when(instrumentStorage.get(instrument.getUid())).thenReturn(instrument);
        candleStorage = mock(CandleStorageInterface.class);

        clock = mock(Clock.class);
        when(clock.currentTime()).thenReturn(currentTime);

        broker = createBroker(candleStorage, instrumentStorage);
        broker.applyContext(
            new SimulationContext(
                mock(SchedulerInterface.class),
                mock(SimulationBrokerContainer.class),
                clock
            )
        );
        testAccount = broker.getUserService().openAccount(
            "testAccount",
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );

        orderService = broker.getOrderService();
    }

    abstract MockBroker createBroker(
        CandleStorageInterface candleStorage,
        InstrumentStorageInterface instrumentStorage
    );

    @Test
    public void testGet() throws AbstractException {
        PostOrderResponse[] postOrderResponses = requestsForTestGet();
        GetOrdersResponse response = orderService.get(GetOrdersRequest.of(testAccount.getId()));

        for (int i = 0; i < response.getOrders().size(); i++) {
            var postOrderResponse = postOrderResponses[i];

            for (var order : response.getOrders()) {
                if (order.getOrderId().equals(postOrderResponse.getOrderId())) {
                    assertEquals(postOrderResponse.getOrderId(), order.getOrderId());
                    assertEquals(postOrderResponse.getDirection(), order.getDirection());
                    assertEquals(postOrderResponse.getOrderType(), order.getOrderType());
                    assertEquals(postOrderResponse.getLotsRequested(), order.getLotsRequested());
                    assertEquals(postOrderResponse.getLotsExecuted(), order.getLotsExecuted());
                    assertEquals(postOrderResponse.getInitialPrice(), order.getInitialOrderPrice());
                    assertEquals(postOrderResponse.getTotalPricePerOne(), order.getExecutedOrderPrice());
                    assertEquals(postOrderResponse.getTotalPrice(), order.getTotalPrice());
                    assertEquals(postOrderResponse.getExecutionStatus(), order.getExecutionStatus());

                    break;
                }
            }
        }
    }

    @Test
    public void testBuyMarket() throws AbstractException {
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INSUFFICIENT_BALANCE,
            () -> assertBuyOrder(testCandle, OrderType.MARKET, 10, testCandle.getOpenPrice())
        );

        addMoney(testCandle.getOpenPrice().multiply(100));
        assertBuyOrder(testCandle, OrderType.MARKET, 12, testCandle.getOpenPrice());

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.QUANTITY_MUST_BE_POSITIVE,
            () -> assertBuyOrder(testCandle, OrderType.MARKET, -10, testCandle.getOpenPrice())
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.QUANTITY_MUST_BE_POSITIVE,
            () -> assertBuyOrder(testCandle, OrderType.MARKET, 0)
        );

        when(instrumentStorage.get(config.getInstrument().getUid())).thenReturn(null);

        assertThrowsWithErrorCode(
            NotFoundException.class,
            ErrorCode.INSTRUMENT_NOT_FOUND,
            () -> assertBuyOrder(testCandle, OrderType.MARKET, 10, testCandle.getOpenPrice())
        );

        verify(candleStorage, atLeastOnce()).getAt(config.getInstrument().getUid(), currentTime);
    }

    @Test
    public void testBuyLimit() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.getOpenPrice().multiply(100));

        assertBuyOrder(validCandle, OrderType.LIMIT, 10, validCandle.getOpenPrice());

        Candle buySignalCandle = createCandle(
            currentTime, 10, 15, 5, 9, 100
        );

        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(buySignalCandle));
        assertBuyOrder(validCandle, OrderType.LIMIT, 10, Quotation.of(9));

        Candle expirationCandle = createCandle(
            currentTime, 10, 15, 5, 12, 100
        );

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(expirationCandle));
        assertBuyOrder(validCandle, OrderType.LIMIT, 10, Quotation.of(9));

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INVALID_PARAMETER_PRICE,
            () -> assertBuyOrder(validCandle, OrderType.LIMIT, 10, Quotation.of(-100))
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INVALID_PARAMETER_PRICE,
            () -> assertBuyOrder(validCandle, OrderType.LIMIT, 10, Quotation.ZERO)
        );

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.MISSING_PARAMETER_PRICE,
            () -> assertBuyOrder(validCandle, OrderType.LIMIT, 10, null)
        );

        verify(candleStorage, atLeastOnce()).getAt(config.getInstrument().getUid(), currentTime);
    }

    @Test
    public void testBuyBestPrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.getOpenPrice().multiply(100));

        assertBuyOrder(validCandle, OrderType.BEST_PRICE, 10, Quotation.of(5));
        verify(candleStorage, atLeastOnce()).getAt(config.getInstrument().getUid(), currentTime);
    }

    @Test
    public void testSellMarket() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        var operationsService = broker.getOperationsService();
        var openPrice = validCandle.getOpenPrice();

        operationsService.addToPosition(testAccount.getId(), instrumentConfig.getUid(), 100);
        assertSellOrder(validCandle, OrderType.MARKET, 15, openPrice);

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INSUFFICIENT_BALANCE,
            () -> assertSellOrder(validCandle, OrderType.MARKET, 1000, openPrice)
        );

        assertSellOrder(validCandle, OrderType.MARKET, 20, openPrice);
        verify(candleStorage, atLeastOnce()).getAt(instrumentConfig.getUid(), currentTime);
    }

    @Test
    public void testSellLimit() throws AbstractException {
        // Test ordinary limit sell order
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addInstruments(80);

        assertSellOrder(validCandle, OrderType.LIMIT, 10, validCandle.getOpenPrice());

        // Test limit sell order on delayed execution
        Candle sellSignalCandle = createCandle(
            currentTime, 10, 15, 5, 12, 100
        );

        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(sellSignalCandle));
        assertSellOrder(validCandle, OrderType.LIMIT, 10, Quotation.of(11));

        // Test limit sell order on expiration
        Candle expirationCandle = createCandle(
            currentTime, 10, 15, 5, 9, 100
        );

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(expirationCandle));
        assertSellOrder(validCandle, OrderType.LIMIT, 10, Quotation.of(11));

        verify(candleStorage, atLeastOnce()).getAt(config.getInstrument().getUid(), currentTime);
    }

    @Test
    public void testSellBestPrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addInstruments(100);

        assertSellOrder(validCandle, OrderType.BEST_PRICE, 10, Quotation.of(11));
        verify(candleStorage, atLeastOnce()).getAt(config.getInstrument().getUid(), currentTime);
    }

    @Test
    public void testGetState() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.getOpenPrice().multiply(100));

        var postResponse = assertBuyOrder(validCandle, OrderType.MARKET, 10, validCandle.getOpenPrice());

        var state = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(postResponse.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertEquals(postResponse.getOrderId(), state.getOrderId());
        assertEquals(postResponse.getDirection(), state.getDirection());
        assertEquals(postResponse.getOrderType(), state.getOrderType());
        assertEquals(postResponse.getLotsRequested(), state.getLotsRequested());
        assertEquals(postResponse.getLotsExecuted(), state.getLotsExecuted());
        assertEquals(postResponse.getInitialPrice(), state.getInitialOrderPrice());
        assertEquals(postResponse.getTotalPricePerOne(), state.getExecutedOrderPrice());
        assertEquals(postResponse.getTotalPrice(), state.getTotalPrice());
        assertEquals(postResponse.getExecutionStatus(), state.getExecutionStatus());

        assertThrowsWithErrorCode(
            NotFoundException.class,
            ErrorCode.ORDER_NOT_FOUND,
            () -> orderService.getState(
                new GetOrderStateRequest()
                    .setOrderId("invalidOrderId")
                    .setAccountId(testAccount.getId())
            )
        );

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

    protected PostOrderResponse assertBuyOrder(
        Candle priceCandle,
        OrderType orderType,
        long quantity
    ) throws AbstractException {
        return assertBuyOrder(priceCandle, orderType, quantity, null);
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

        return new Candle()
            .setTime(time)
            .setClosePrice(Quotation.of(closePrice))
            .setHighPrice(Quotation.of(highPrice))
            .setLowPrice(Quotation.of(lowPrice))
            .setOpenPrice(Quotation.of(openPrice))
            .setVolume(volume)
            .setInstrumentUid(instrumentConfig.getUid());
    }

    protected Quotation calculateInitialPricePerOne(
        Candle priceCandle,
        OrderType orderType,
        double bestPriceRatio
    ) {
        return switch (orderType) {
            case MARKET, LIMIT -> priceCandle.getOpenPrice();
            case BEST_PRICE -> priceCandle
                .getLowPrice()
                .add(
                    priceCandle
                        .getHighPrice()
                        .subtract(priceCandle.getLowPrice())
                        .multiply(bestPriceRatio)
                );
            default -> throw new IllegalArgumentException("Unexpected value: " + orderType);
        };
    }

    protected <T extends AbstractException> void assertThrowsWithErrorCode(
        Class<T> exceptionClass,
        ErrorCode errorCode,
        Executable executable
    ) {
        T exception = assertThrows(exceptionClass, executable);
        assertEquals(errorCode, exception.getErrorCode());
    }

    protected PostOrderResponse assertSellOrder(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        MockOrderService ordersService = broker.getOrderService();
        MockOperationsService operationsService = broker.getOperationsService();
        boolean isDelayedLimitOrder = orderType == OrderType.LIMIT
            && priceLimit != null
            && priceLimit.isMore(priceCandle.getOpenPrice());

        when(candleStorage.getAt(instrumentConfig.getUid(), priceCandle.getTime()))
            .thenReturn(Optional.of(priceCandle));
        when(clock.currentTime()).thenReturn(priceCandle.getTime());

        PostOrderRequest request = new PostOrderRequest()
            .setInstrumentId(instrumentConfig.getUid())
            .setQuantity(quantity)
            .setPrice(priceLimit)
            .setAccountId(testAccount.getId())
            .setDirection(OrderDirection.SELL)
            .setOrderType(orderType);

        Position position = operationsService.getPositionByInstrumentId(testAccount.getId(), instrumentConfig.getUid());
        long instrumentBalance = (position != null) ? position.getBalance() : 0;
        Money avialableMoney = operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency());

        PostOrderResponse postResponse = ordersService.post(request);
        Quotation initialPricePerOne = calculateInitialPricePerOne(
            priceCandle,
            orderType,
            orderService.getSellBestPriceRatio()
        );

        if (isDelayedLimitOrder) {
            assertEquals(0, postResponse.getLotsExecuted());
            assertEquals(quantity, postResponse.getLotsRequested());
            assertEquals(Quotation.ZERO, postResponse.getTotalPrice().getQuotation());
            assertEquals(Quotation.ZERO, postResponse.getTotalPricePerOne().getQuotation());
            assertEquals(ExecutionStatus.NEW, postResponse.getExecutionStatus());
            assertEquals(
                avialableMoney,
                operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
            );
            assertEquals(
                instrumentBalance,
                operationsService.getPositionByInstrumentId(testAccount.getId(), instrumentConfig.getUid()).getBalance()
            );
        } else {
            Quotation totalPricePerOne = initialPricePerOne.subtract(initialPricePerOne.multiply(commissionRatio));

            assertEquals(quantity, postResponse.getLotsExecuted());
            assertEquals(quantity, postResponse.getLotsRequested());
            assertEquals(totalPricePerOne.multiply(quantity), postResponse.getTotalPrice().getQuotation());
            assertEquals(totalPricePerOne, postResponse.getTotalPricePerOne().getQuotation());
            assertEquals(initialPricePerOne.multiply(quantity), postResponse.getInitialPrice().getQuotation());
            assertEquals(initialPricePerOne, postResponse.getInitialPricePerOne().getQuotation());
            assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());

            long newInstrumentBalance = instrumentBalance - quantity;
            Money newAvialableMoney = avialableMoney.add(
                Money.of(instrumentConfig.getCurrency(), totalPricePerOne.multiply(quantity))
            );

            assertEquals(
                newInstrumentBalance,
                operationsService.getPositionByInstrumentId(testAccount.getId(), instrumentConfig.getUid()).getBalance()
            );
            assertEquals(
                newAvialableMoney,
                operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
            );
        }

        assertEquals(initialPricePerOne.multiply(quantity), postResponse.getInitialPrice().getQuotation());
        assertEquals(initialPricePerOne, postResponse.getInitialPricePerOne().getQuotation());
        assertNotNull(postResponse.getOrderId());
        assertEquals(OrderDirection.SELL, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());

        return postResponse;
    }

    protected PostOrderResponse assertBuyOrder(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();
        var operationsService = broker.getOperationsService();
        var isDelayedLimitOrder = orderType == OrderType.LIMIT
            && priceLimit != null
            && priceLimit.isLess(priceCandle.getOpenPrice());

        when(candleStorage.getAt(instrumentConfig.getUid(), priceCandle.getTime()))
            .thenReturn(Optional.of(priceCandle));
        when(clock.currentTime()).thenReturn(priceCandle.getTime());

        var request = new PostOrderRequest()
            .setInstrumentId(instrumentConfig.getUid())
            .setQuantity(quantity)
            .setPrice(priceLimit)
            .setAccountId(testAccount.getId())
            .setDirection(OrderDirection.BUY)
            .setOrderType(orderType);

        var position = operationsService.getPositionByInstrumentId(testAccount.getId(), instrumentConfig.getUid());
        var instrumentBalance = (position != null) ? position.getBalance() : 0;
        var avialableMoney = operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency());
        Quotation initialPricePerOne = calculateInitialPricePerOne(
            priceCandle,
            orderType,
            orderService.getBuyBestPriceRatio()
        );

        PostOrderResponse postResponse = ordersService.post(request);

        assertNotNull(postResponse.getOrderId());
        assertEquals(OrderDirection.BUY, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(initialPricePerOne.multiply(quantity), postResponse.getInitialPrice().getQuotation());
        assertEquals(initialPricePerOne, postResponse.getInitialPricePerOne().getQuotation());

        if (isDelayedLimitOrder) {
            assertEquals(0, postResponse.getLotsExecuted());
            assertEquals(quantity, postResponse.getLotsRequested());
            assertEquals(Quotation.ZERO, postResponse.getTotalPrice().getQuotation());
            assertEquals(Quotation.ZERO, postResponse.getTotalPricePerOne().getQuotation());
            assertEquals(ExecutionStatus.NEW, postResponse.getExecutionStatus());
            var positionAfter = operationsService.getPositionByInstrumentId(
                testAccount.getId(),
                instrumentConfig.getUid()
            );
            var balanceAfter = (positionAfter != null) ? positionAfter.getBalance() : 0;
            assertEquals(
                avialableMoney,
                operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
            );
            Assertions.assertEquals(instrumentBalance, balanceAfter);
        } else {
            var totalPricePerOne = initialPricePerOne.add(initialPricePerOne.multiply(commissionRatio));

            assertEquals(quantity, postResponse.getLotsExecuted());
            assertEquals(quantity, postResponse.getLotsRequested());
            assertEquals(totalPricePerOne.multiply(quantity), postResponse.getTotalPrice().getQuotation());
            assertEquals(totalPricePerOne, postResponse.getTotalPricePerOne().getQuotation());
            assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());

            var newAvialableMoney = avialableMoney.subtract(
                Money.of(instrumentConfig.getCurrency(), totalPricePerOne.multiply(quantity))
            );
            assertEquals(
                newAvialableMoney,
                operationsService.getAvailableMoney(testAccount.getId(), instrumentConfig.getCurrency())
            );
        }

        return postResponse;
    }

    abstract protected PostOrderResponse[] requestsForTestGet() throws AbstractException;
}
