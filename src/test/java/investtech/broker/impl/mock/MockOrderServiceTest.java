package investtech.broker.impl.mock;

import investtech.broker.container.emulation.SimulationBrokerContainer;
import investtech.broker.contract.service.exception.*;
import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.order.request.GetOrderStateRequest;
import investtech.broker.contract.service.order.request.OrderDirection;
import investtech.broker.contract.service.order.request.OrderType;
import investtech.broker.contract.service.order.request.PostOrderRequest;
import investtech.broker.contract.service.order.response.ExecutionStatus;
import investtech.broker.contract.service.order.response.PostOrderResponse;
import investtech.broker.contract.service.user.AccessLevel;
import investtech.broker.contract.service.user.Account;
import investtech.broker.contract.service.user.AccountType;
import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.broker.impl.mock.config.InstrumentConfig;
import investtech.broker.impl.mock.config.MockBrokerConfig;
import investtech.simulation.shared.instrument.InstrumentStorageInterface;
import investtech.simulation.shared.market.candle.Candle;
import investtech.simulation.shared.market.candle.CandleStorageInterface;
import investtech.strategy.context.TimeSynchronizerInterface;
import investtech.strategy.context.emulation.SimulationContext;
import investtech.strategy.scheduler.SchedulerInterface;
import investtech.test.util.ConfigLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MockOrderServiceTest {
    protected static final String SETTINGS_PATH = "src/test/resources/broker.mock/test-settings.yaml";
    protected MockBroker broker;
    protected MockBrokerConfig config;
    protected MockOrderService orderService;
    protected CandleStorageInterface candleStorage;
    protected Account testAccount;
    protected Instant currentTime;
    protected Quotation commissionRatio;

    @BeforeEach
    public void setUp() throws Exception {
        config = ConfigLoader.load(MockBrokerConfig.class, SETTINGS_PATH);
        commissionRatio = Quotation.of(config.getOrderService().getCommissionRatio());

        Instrument instrument = createInstrument(config.getInstrument());
        currentTime = Instant.parse("2021-12-15T15:00:00Z");

        InstrumentStorageInterface instrumentStorage = mock(InstrumentStorageInterface.class);
        when(instrumentStorage.get(instrument.getUid())).thenReturn(instrument);
        candleStorage = mock(CandleStorageInterface.class);

        TimeSynchronizerInterface timeSynchronizer = mock(TimeSynchronizerInterface.class);
        when(timeSynchronizer.currentTime()).thenReturn(currentTime);

        broker = new MockBroker(candleStorage, instrumentStorage);
        broker.applyContext(
            new SimulationContext(
                mock(SchedulerInterface.class),
                mock(SimulationBrokerContainer.class),
                timeSynchronizer
            )
        );
        testAccount = broker.getUserService().openAccount(
            "testAccount",
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );

        orderService = broker.getOrderService();
    }

    @Test
    public void testThrowException() {
        InstrumentConfig instrumentConfig = config.getInstrument();
        when(candleStorage.getAt(any(), any())).thenReturn(Optional.of(createCandle(currentTime, 7, 8, 9, 1, 100, instrumentConfig.getUid())));

        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INSUFFICIENT_BALANCE,
            () -> orderService.post(
                new PostOrderRequest()
                    .setInstrumentId(instrumentConfig.getUid())
                    .setQuantity(10)
                    .setAccountId(testAccount.getId())
                    .setDirection(OrderDirection.BUY)
                    .setOrderType(OrderType.MARKET)
            )
        );
        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.INVALID_PARAMETER_PRICE,
            () -> orderService.post(
                new PostOrderRequest()
                    .setInstrumentId(instrumentConfig.getUid())
                    .setQuantity(10)
                    .setAccountId(testAccount.getId())
                    .setDirection(OrderDirection.BUY)
                    .setOrderType(OrderType.LIMIT)
                    .setPrice(Quotation.of(-100))
            )
        );
    }

    @Test
    public void testBuyMarket() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        Candle testCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100, instrumentConfig.getUid()
        );
        when(candleStorage.getAt(any(), any())).thenReturn(Optional.empty());
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime)).thenReturn(Optional.of(testCandle));

        broker.getOperationsService().addMoney(
            testAccount.getId(),
            Money.of(
                config.getInstrument().getCurrency(),
                testCandle.getOpenPrice().multiply(100)
            )
        );

        PostOrderResponse postResponse = orderService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(15)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.BUY)
                .setOrderType(OrderType.MARKET)
                .setPrice(testCandle.getOpenPrice())
        );

        Quotation actualPrice = testCandle.getOpenPrice().add(testCandle.getOpenPrice().multiply(commissionRatio));
        assertPostOrderResponse(
            postResponse,
            15,
            OrderDirection.BUY,
            instrumentConfig.getUid(),
            actualPrice,
            testCandle.getOpenPrice()
        );

        verify(candleStorage).getAt(instrumentConfig.getUid(), currentTime);
    }

    @Test
    public void testBuyLimit() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();

        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        Candle invalidCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(1))
            .setHighPrice(Quotation.of(1))
            .setLowPrice(Quotation.of(1))
            .setOpenPrice(Quotation.of(1))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(invalidCandle));
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));

        broker.getOperationsService().addMoney(
            testAccount.getId(),
            Money.of(
                config.getInstrument().getCurrency(),
                validCandle.getOpenPrice().multiply(100)
            )
        );

        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(10)
                .setPrice(validCandle.getOpenPrice())
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.BUY)
                .setOrderType(OrderType.LIMIT)
        );

        Quotation totalPrice = validCandle.getOpenPrice()
            .multiply(commissionRatio.add(1));

        assertNotNull(postResponse.getOrderId());
        assertEquals(10, postResponse.getLotsExecuted());
        assertEquals(10, postResponse.getLotsRequested());
        assertEquals(OrderDirection.BUY, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(totalPrice.multiply(10), postResponse.getTotalPrice().getQuotation());
        assertEquals(totalPrice, postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(validCandle.getOpenPrice().multiply(10), postResponse.getInitialPrice().getQuotation());
        assertEquals(validCandle.getOpenPrice(), postResponse.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());

        UnimplementedException exception = assertThrows(
            UnimplementedException.class,
            () -> ordersService.post(
                new PostOrderRequest()
                    .setInstrumentId(instrumentConfig.getUid())
                    .setQuantity(10)
                    .setPrice(Quotation.of(5))
                    .setAccountId(testAccount.getId())
                    .setDirection(OrderDirection.BUY)
                    .setOrderType(OrderType.LIMIT)
            )
        );
        assertEquals(ErrorCode.UNIMPLEMENTED, exception.getErrorCode());

        verify(candleStorage, times(2)).getAt(instrumentConfig.getUid(), currentTime);
    }

    @Test
    public void testBuyBestPrice() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();
        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        Candle invalidCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(1))
            .setHighPrice(Quotation.of(1))
            .setLowPrice(Quotation.of(1))
            .setOpenPrice(Quotation.of(1))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(invalidCandle));
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));

        broker.getOperationsService().addMoney(
            testAccount.getId(),
            Money.of(
                config.getInstrument().getCurrency(),
                validCandle.getOpenPrice().multiply(100)
            )
        );

        long quantity = 10;
        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(quantity)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.BUY)
                .setOrderType(OrderType.BESTPRICE)
        );

        double bestBuyPriceRatio = orderService.getBuyBestPriceRatio();

        // bestBuyPrice = lowPrice + (highPrice - lowPrice) * bestBuyPriceRatio
        Quotation bestBuyPrice = validCandle.getLowPrice()
            .add(
                validCandle
                    .getHighPrice()
                    .subtract(validCandle.getLowPrice())
                    .multiply(bestBuyPriceRatio)
            );

        // totalPrice = bestBuyPrice * (1 + commissionRatio) * quantity
        Quotation totalPrice = bestBuyPrice
            .multiply(commissionRatio.add(1))
            .multiply(quantity);

        assertNotNull(postResponse.getOrderId());
        assertEquals(quantity, postResponse.getLotsExecuted());
        assertEquals(quantity, postResponse.getLotsRequested());
        assertEquals(OrderDirection.BUY, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(totalPrice, postResponse.getTotalPrice().getQuotation());
        assertEquals(totalPrice.divide(quantity), postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(bestBuyPrice.multiply(quantity), postResponse.getInitialPrice().getQuotation());
        assertEquals(bestBuyPrice, postResponse.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());
    }

    @Test
    public void testSellMarket() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        String currency = instrumentConfig.getCurrency();
        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.empty());
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));

        var ordersService = broker.getOrderService();
        var operationsService = broker.getOperationsService();
        var openPrice = validCandle.getOpenPrice();

        operationsService.addToPosition(testAccount.getId(), instrumentConfig.getUid(), 100);

        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(15)
                .setPrice(openPrice)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.SELL)
                .setOrderType(OrderType.MARKET)
        );

        // actualPrice = openPrice + openPrice * commissionRatio
        Quotation actualPrice = openPrice
            .add(openPrice.multiply(commissionRatio));

        assertNotNull(postResponse.getOrderId());
        assertEquals(15, postResponse.getLotsExecuted());
        assertEquals(15, postResponse.getLotsRequested());
        assertEquals(OrderDirection.SELL, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(actualPrice.multiply(15), postResponse.getTotalPrice().getQuotation());
        assertEquals(actualPrice, postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(openPrice.multiply(15), postResponse.getInitialPrice().getQuotation());
        assertEquals(openPrice, postResponse.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());

        var exception = assertThrows(
            InvalidRequestException.class,
            () -> ordersService.post(
                new PostOrderRequest()
                    .setInstrumentId(instrumentConfig.getUid())
                    .setQuantity(1000)
                    .setPrice(openPrice)
                    .setAccountId(testAccount.getId())
                    .setDirection(OrderDirection.SELL)
                    .setOrderType(OrderType.MARKET)
            )
        );
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());

        ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(10)
                .setPrice(openPrice)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.SELL)
                .setOrderType(OrderType.MARKET)
        );

        assertEquals(
            Money.of(currency, actualPrice.multiply(25)),
            operationsService.getAvailableMoney(
                testAccount.getId(),
                instrumentConfig.getCurrency()
            )
        );
        assertEquals(
            75,
            operationsService.getPositionByInstrumentId(
                testAccount.getId(),
                instrumentConfig.getUid()
            ).getBalance()
        );

        verify(candleStorage, times(3)).getAt(instrumentConfig.getUid(), currentTime);
    }

    @Test
    public void testSellLimit() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();

        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        Candle invalidCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(1))
            .setHighPrice(Quotation.of(1))
            .setLowPrice(Quotation.of(1))
            .setOpenPrice(Quotation.of(1))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(invalidCandle));
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));

        broker.getOperationsService().addToPosition(testAccount.getId(), instrumentConfig.getUid(), 100);

        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(10)
                .setPrice(validCandle.getOpenPrice())
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.SELL)
                .setOrderType(OrderType.LIMIT)
        );

        Quotation totalPrice = validCandle.getOpenPrice()
            .multiply(commissionRatio.add(1));

        assertNotNull(postResponse.getOrderId());
        assertEquals(10, postResponse.getLotsExecuted());
        assertEquals(10, postResponse.getLotsRequested());
        assertEquals(OrderDirection.SELL, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(totalPrice.multiply(10), postResponse.getTotalPrice().getQuotation());
        assertEquals(totalPrice, postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(validCandle.getOpenPrice().multiply(10), postResponse.getInitialPrice().getQuotation());
        assertEquals(validCandle.getOpenPrice(), postResponse.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());

        UnimplementedException exception = assertThrows(
            UnimplementedException.class,
            () -> ordersService.post(
                new PostOrderRequest()
                    .setInstrumentId(instrumentConfig.getUid())
                    .setQuantity(10)
                    .setPrice(Quotation.of(11))
                    .setAccountId(testAccount.getId())
                    .setDirection(OrderDirection.SELL)
                    .setOrderType(OrderType.LIMIT)
            )
        );
        assertEquals(ErrorCode.UNIMPLEMENTED, exception.getErrorCode());

        verify(candleStorage, times(2)).getAt(instrumentConfig.getUid(), currentTime);
    }

    @Test
    public void testSellBestPrice() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();
        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        Candle invalidCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(1))
            .setHighPrice(Quotation.of(1))
            .setLowPrice(Quotation.of(1))
            .setOpenPrice(Quotation.of(1))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(invalidCandle));
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));

        broker.getOperationsService().addToPosition(testAccount.getId(), instrumentConfig.getUid(), 100);

        long quantity = 10;
        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(quantity)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.SELL)
                .setOrderType(OrderType.BESTPRICE)
        );

        double bestSellPriceRatio = orderService.getSellBestPriceRatio();

        // bestSellPrice = lowPrice + (highPrice - lowPrice) * bestSellPriceRatio
        Quotation bestSellPrice = validCandle.getLowPrice()
            .add(
                validCandle
                    .getHighPrice()
                    .subtract(validCandle.getLowPrice())
                    .multiply(bestSellPriceRatio)
            );

        // totalPrice = bestSellPrice * (1 + commissionRatio) * quantity
        Quotation totalPrice = bestSellPrice
            .multiply(commissionRatio.add(1))
            .multiply(quantity);

        assertNotNull(postResponse.getOrderId());
        assertEquals(quantity, postResponse.getLotsExecuted());
        assertEquals(quantity, postResponse.getLotsRequested());
        assertEquals(OrderDirection.SELL, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(totalPrice, postResponse.getTotalPrice().getQuotation());
        assertEquals(totalPrice.divide(quantity), postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(bestSellPrice.multiply(quantity), postResponse.getInitialPrice().getQuotation());
        assertEquals(bestSellPrice, postResponse.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());
    }

    @Test
    public void testGetState() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();
        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        Candle invalidCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(1))
            .setHighPrice(Quotation.of(1))
            .setLowPrice(Quotation.of(1))
            .setOpenPrice(Quotation.of(1))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(invalidCandle));
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));

        broker.getOperationsService().addMoney(
            testAccount.getId(),
            Money.of(
                config.getInstrument().getCurrency(),
                validCandle.getOpenPrice().multiply(100)
            )
        );

        var postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(config.getInstrument().getUid())
                .setQuantity(10)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.BUY)
                .setOrderType(OrderType.MARKET)
        );

        var state = ordersService.getState(
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
        assertEquals(postResponse.getTotalPrice(), state.getTotalOrderAmount());
        assertEquals(postResponse.getExecutionStatus(), state.getExecutionStatus());

        var exception = assertThrows(
            NotFoundException.class,
            () -> ordersService.getState(
                new GetOrderStateRequest()
                    .setOrderId("invalidOrderId")
                    .setAccountId(testAccount.getId())
            )
        );
        assertEquals(ErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());
    }

    protected void assertBuyOrder(
        Candle priceCandle,
        OrderType orderType,
        long quantity,
        Quotation priceLimit
    ) throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();
        var operationsService = broker.getOperationsService();

        when(candleStorage.getAt(any(), any())).thenReturn(Optional.empty());
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime)).thenReturn(Optional.of(priceCandle));

        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(quantity)
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.BUY)
                .setOrderType(orderType)
                .setPrice(priceLimit)
        );

        // actualPrice = openPrice + openPrice * commissionRatio
        Quotation totalPricePerOne = priceCandle.getOpenPrice().add(priceCandle.getOpenPrice().multiply(commissionRatio));
        assertNotNull(postResponse.getOrderId());
        assertEquals(lotsExecuted, postResponse.getLotsExecuted());
        assertEquals(lotsExecuted, postResponse.getLotsRequested());
        assertEquals(direction, postResponse.getDirection());
        assertEquals(instrumentUid, postResponse.getInstrumentUid());
        assertEquals(totalPricePerOne.multiply(lotsExecuted), postResponse.getTotalPrice().getQuotation());
        assertEquals(totalPricePerOne, postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(openPrice.multiply(lotsExecuted), postResponse.getInitialPrice().getQuotation());
        assertEquals(openPrice, response.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.FILL, postResponse.getExecutionStatus());
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
        int volume,
        String instrumentUid
    ) {
        return new Candle()
            .setTime(time)
            .setClosePrice(Quotation.of(closePrice))
            .setHighPrice(Quotation.of(highPrice))
            .setLowPrice(Quotation.of(lowPrice))
            .setOpenPrice(Quotation.of(openPrice))
            .setVolume(volume)
            .setInstrumentUid(instrumentUid);
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
