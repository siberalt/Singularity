package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.shared.SandboxBrokerFacade;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.config.MockBrokerConfig;
import com.siberalt.singularity.entity.instrument.InstrumentRepository;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.simulation.UserActionSimulator;
import com.siberalt.singularity.test.util.ConfigLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EventSimulatedOrderServiceIT {
    private static Logger logger;
    private EventMockBroker broker;
    private UserActionSimulator<Map<String, Object>> userActionSimulator;
    private ReadCandleRepository candleStorage;
    private SimulationClock clock;
    private EventSimulator eventSimulator;
    private MockBrokerConfig config;
    private MockOperationsService operationsService;
    private final HashMap<String, Object> userContext = new HashMap<>();

    @BeforeAll
    public static void setUpAll() {
        logger = Logger.getLogger(EventSimulatedOrderService.class.getName());
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new java.util.logging.Formatter() {
            @Override
            public String format(LogRecord record) {
                return String.format("%s: %s\n", record.getLevel(), record.getMessage());
            }
        });
        logger.setUseParentHandlers(false);
        logger.addHandler(consoleHandler);
    }

    @BeforeEach
    public void setUp() throws Exception {
        config = ConfigLoader.load(MockBrokerConfig.class, MockBrokerConfig.SETTINGS_PATH);

        var instrument = new Instrument()
            .setCurrency("RUB")
            .setLot(config.getInstrument().getLot())
            .setInstrumentType(config.getInstrument().getInstrumentType())
            .setUid(config.getInstrument().getUid());

        userActionSimulator = new UserActionSimulator<>(userContext);
        candleStorage = mock(CvsCandleRepository.class);

        InstrumentRepository instrumentRepository = new InMemoryInstrumentRepository();
        instrumentRepository.save(instrument);
        OrderRepository orderRepository = new InMemoryOrderRepository();
        clock = new SimpleSimulationClock();
        broker = new EventMockBroker(candleStorage, instrumentRepository, orderRepository, clock);

        EventSimulatedOrderService orderService = broker.getOrderService();
        operationsService = broker.getOperationsService();
        orderService.setCommissionRatio(0);

        eventSimulator = new EventSimulator(clock);
        eventSimulator.addEventInvoker(orderService);
        eventSimulator.addEventInvoker(userActionSimulator);
        eventSimulator.addInitializableUnit(userActionSimulator);
        eventSimulator.addTimeDependentUnit(userActionSimulator);
        eventSimulator.addTimeDependentUnit(orderService);
    }

    @Test
    public void testPostBestPriceOrders() throws AbstractException {
        Instant openingTime = Instant.parse("1997-05-02T08:10:00.00Z");
        Instant buyingTime = Instant.parse("1997-05-03T08:15:00.00Z");
        Instant sellingTime = Instant.parse("1997-05-04T08:20:00.00Z");
        Money initialMoney = Money.of("RUB", Quotation.of(1000000));
        String instrumentUid = config.getInstrument().getUid();

        SandboxBrokerFacade brokerFacade = SandboxBrokerFacade.of(broker);
        when(candleStorage.findClosestBefore(instrumentUid, buyingTime))
            .thenReturn(Optional.of(Candle.of(buyingTime, 1000, 7.1)));
        when(candleStorage.findClosestBefore(instrumentUid, sellingTime))
            .thenReturn(Optional.of(Candle.of(sellingTime, 1000, 7.2)));

        userActionSimulator.planAction(openingTime, (userContext) -> {
            System.out.printf("[%s]: Opening account\n", clock.currentTime());
            String accountId = brokerFacade.openAccount("testAccount");
            brokerFacade.payIn(accountId, initialMoney);
            userContext.put("accountId", accountId);
        });
        userActionSimulator.planAction(buyingTime, (userContext) -> {
            System.out.printf("[%s]: Best price buy\n", clock.currentTime());
            brokerFacade.buyBestPriceUnchecked((String) userContext.get("accountId"), instrumentUid, 20);
        });
        userActionSimulator.planAction(sellingTime, (userContext) -> {
            System.out.printf("[%s]: Sell best price\n", clock.currentTime());
            brokerFacade.sellBestPriceUnchecked((String) userContext.get("accountId"), instrumentUid, 20);
        });

        eventSimulator.run(
            Instant.parse("1997-05-02T08:10:00.00Z"),
            Instant.parse("1997-05-04T08:20:00.00Z")
        );

        // Assert balance after simulation
        Money money = operationsService.getAvailableMoney((String) userContext.get("accountId"), "RUB");
        Quotation expectedMoney = initialMoney.getQuotation()
            .subtract(Quotation.of(20).multiply(Quotation.of(7.1)))
            .add(Quotation.of(20).multiply(Quotation.of(7.2)));

        Assertions.assertNotNull(money);
        Assertions.assertEquals(expectedMoney, money.getQuotation());
    }

    @Test
    public void testPostDelayedLimitOrders() throws AbstractException {
        SandboxBrokerFacade brokerFacade = SandboxBrokerFacade.of(broker);
        Money initialMoney = Money.of("RUB", Quotation.of(1000000));
        String instrumentUid = config.getInstrument().getUid();

        Instant openingTime = Instant.parse("1997-05-02T08:10:00.00Z");
        Instant postBuyOrderTime = Instant.parse("1997-05-03T08:15:00.00Z");
        Instant executeBuyOrderTime = Instant.parse("1997-05-03T08:20:00.00Z");
        Instant postSellOrderTime = Instant.parse("1997-05-04T08:20:00.00Z");
        Instant executeSellOrderTime = Instant.parse("1997-05-04T14:25:00.00Z");

        when(candleStorage.findClosestBefore(instrumentUid, postBuyOrderTime))
            .thenReturn(Optional.of(Candle.of(postBuyOrderTime, 1000, 7.3)));
        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(Candle.of(executeBuyOrderTime, 1000, 7)))
            .thenReturn(List.of(Candle.of(executeSellOrderTime, 1000, 8)));
        when(candleStorage.findClosestBefore(instrumentUid, postSellOrderTime))
            .thenReturn(Optional.of(Candle.of(postSellOrderTime, 1000, 6)));

        userActionSimulator.planAction(openingTime, (userContext) -> {
            System.out.printf("[%s]: Opening account\n", clock.currentTime());
            String accountId = brokerFacade.openAccount("testAccount");
            brokerFacade.payIn(accountId, initialMoney);
            userContext.put("accountId", accountId);
        });
        userActionSimulator.planAction(postBuyOrderTime, (userContext) -> {
            System.out.printf("[%s]: Limit buy\n", clock.currentTime());
            brokerFacade.buyLimitUnchecked((String) userContext.get("accountId"), instrumentUid, 20, 7);
        });
        userActionSimulator.planAction(postSellOrderTime, (userContext) -> {
            System.out.printf("[%s]: Limit sell\n", clock.currentTime());
            brokerFacade.sellLimitUnchecked((String) userContext.get("accountId"), instrumentUid, 10, 8);
        });

        eventSimulator.run(
            Instant.parse("1997-05-02T08:10:00.00Z"),
            Instant.parse("1997-05-07T08:20:00.00Z")
        );

        // Verify that the mocks were called at least once
        verify(candleStorage, times(1)).findClosestBefore(instrumentUid, postBuyOrderTime);
        verify(candleStorage, times(2)).findByOpenPrice(any());
        verify(candleStorage, times(1)).findClosestBefore(instrumentUid, postSellOrderTime);

        // Assert balance after simulation
        var money = operationsService.getAvailableMoney((String) userContext.get("accountId"), "RUB");
        Quotation expectedMoney = initialMoney.getQuotation()
            .subtract(Quotation.of(20).multiply(Quotation.of(7))) // Buy 20 at 7
            .add(Quotation.of(10).multiply(Quotation.of(8))); // Sell 10 at 8

        Assertions.assertNotNull(money);
        Assertions.assertEquals(expectedMoney, money.getQuotation());
    }

    @Test
    public void testPostOrders() throws AbstractException {
        Instant startTime = Instant.parse("2000-05-02T09:00:00.00Z");
        Instant postBuyLimitTime = startTime.plus(20, java.time.temporal.ChronoUnit.MINUTES);
        Instant postSellLimitTime = startTime.plus(40, java.time.temporal.ChronoUnit.MINUTES);
        Instant executeSellLimitTime = startTime.plus(42, java.time.temporal.ChronoUnit.MINUTES);
        Instant postBuyMarketTime = startTime.plus(48, java.time.temporal.ChronoUnit.MINUTES);
        Instant executeBuyLimitTime = startTime.plus(50, java.time.temporal.ChronoUnit.MINUTES);
        Instant endTime = startTime.plus(60, java.time.temporal.ChronoUnit.MINUTES);

        String instrumentUid = config.getInstrument().getUid();
        when(candleStorage.findClosestBefore(instrumentUid, postBuyLimitTime))
            .thenReturn(Optional.of(Candle.of(postBuyLimitTime, 1000, 6)));
        when(candleStorage.findClosestBefore(instrumentUid, postSellLimitTime))
            .thenReturn(Optional.of(Candle.of(postSellLimitTime, 1000, 6)));
        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(Candle.of(executeBuyLimitTime, 1000, 4)))
            .thenReturn(List.of(Candle.of(executeSellLimitTime, 1000, 8)));
        when(candleStorage.findClosestBefore(instrumentUid, postBuyMarketTime))
            .thenReturn(Optional.of(Candle.of(postBuyMarketTime, 1000, 5)));

        Money initialMoney = Money.of("RUB", Quotation.of(1000000));
        SandboxBrokerFacade brokerFacade = SandboxBrokerFacade.of(broker);

        userActionSimulator.planAction(startTime, (userContext) -> {
            System.out.printf("[%s]: Opening account\n", clock.currentTime());
            String accountId = brokerFacade.openAccount("testAccount");
            brokerFacade.addToPosition(accountId, instrumentUid, 10);
            brokerFacade.payIn(accountId, initialMoney);
            userContext.put("accountId", accountId);
        });
        userActionSimulator.planAction(postBuyLimitTime, (userContext) -> {
            System.out.printf("[%s]: Post buy limit order\n", clock.currentTime());
            brokerFacade.buyLimitUnchecked((String) userContext.get("accountId"), instrumentUid, 20, 5);
        });
        userActionSimulator.planAction(postSellLimitTime, (userContext) -> {
            System.out.printf("[%s]: Post sell limit order\n", clock.currentTime());
            brokerFacade.sellLimitUnchecked((String) userContext.get("accountId"), instrumentUid, 10, 7);
        });
        userActionSimulator.planAction(postBuyMarketTime, (userContext) -> {
            System.out.printf("[%s]: Post buy market order\n", clock.currentTime());
            brokerFacade.buyMarketUnchecked((String) userContext.get("accountId"), instrumentUid, 40);
        });

        eventSimulator.run(startTime, endTime);

        // Assert balance after simulation
        Money money = operationsService.getAvailableMoney((String) userContext.get("accountId"), "RUB");
        Quotation expectedMoney = initialMoney.getQuotation()
            .subtract(Quotation.of(20).multiply(Quotation.of(4))) // Buy 20 at 4
            .add(Quotation.of(10).multiply(Quotation.of(8))) // Sell 10 at 8
            .subtract(Quotation.of(40).multiply(Quotation.of(5))); // Buy 40 at 5

        Assertions.assertNotNull(money);
        Assertions.assertEquals(expectedMoney, money.getQuotation());
    }
}
