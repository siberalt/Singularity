package investtech.broker.impl.mock.simulation;

import investtech.broker.container.emulation.SimulationBrokerContainer;
import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.response.LastPrice;
import investtech.broker.contract.service.order.request.OrderDirection;
import investtech.broker.contract.service.order.request.OrderType;
import investtech.broker.contract.service.order.request.PostOrderRequest;
import investtech.broker.contract.service.order.response.ExecutionStatus;
import investtech.broker.contract.service.order.response.PostOrderResponse;
import investtech.broker.contract.service.user.AccessLevel;
import investtech.broker.contract.service.user.AccountType;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.broker.impl.mock.simulation.config.InstrumentConfig;
import investtech.broker.impl.mock.simulation.config.MockBrokerConfig;
import investtech.simulation.EventObserver;
import investtech.simulation.Simulator;
import investtech.simulation.shared.instrument.RuntimeInstrumentStorage;
import investtech.simulation.shared.market.candle.factory.CvsFileCandleStorageFactory;
import investtech.simulation.shared.market.candle.storage.cvs.CvsCandleStorage;
import investtech.strategy.StrategyInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.emulation.SimulationContext;
import investtech.strategy.context.emulation.time.SimulationTimeSynchronizer;
import investtech.strategy.scheduler.emulation.SimulationScheduler;
import investtech.test.util.ConfigLoader;
import investtech.test.util.resource.ResourceHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MockOrderServiceTest {
    protected static final String SETTINGS_PATH = "src/test/resources/broker.mock/test-settings.yaml";
    private MockBroker broker;
    private ResourceHandler<CvsCandleStorage> candleStorageHandler;
    private MockBrokerConfig config;
    private SimulationTimeSynchronizer timeSynchronizer;
    private MockOrderService orderService;
    private Simulator simulator;

    @BeforeEach
    void setUp() throws Exception {
        config = ConfigLoader.load(MockBrokerConfig.class, SETTINGS_PATH);

        var instrument = new Instrument()
            .setCurrency(config.getInstrument().getCurrency())
            .setLot(config.getInstrument().getLot())
            .setInstrumentType(config.getInstrument().getInstrumentType())
            .setUid(config.getInstrument().getUid());

        var instrumentStorage = new RuntimeInstrumentStorage().add(instrument);

        candleStorageHandler = ResourceHandler.newHandler(() ->
            new CvsFileCandleStorageFactory().create(
                config.getInstrument().getUid(),
                config.getInstrument().getDataPath()
            )
        );

        timeSynchronizer = new SimulationTimeSynchronizer();
        EventObserver eventObserver = new EventObserver();
        SimulationScheduler scheduler = new SimulationScheduler();
        broker = new MockBroker(candleStorageHandler.create(), instrumentStorage);
        SimulationContext context = new SimulationContext(
            scheduler,
            new SimulationBrokerContainer(Map.of(config.getBrokerId(), broker)),
            timeSynchronizer
        );
        orderService = broker.getOrderService();

        broker.applyContext(context);
        scheduler.applyContext(context);

        scheduler.observeEventsBy(eventObserver);
        orderService.observeEventsBy(eventObserver);
        orderService.setOrderCommissionRatio(config.getOrderService().getCommissionRatio());

        simulator = new Simulator(eventObserver, timeSynchronizer);
    }

    @Test
    void testBasic() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        timeSynchronizer.syncCurrentTime(Instant.parse("2021-12-15T15:00:00Z"));

        var ordersService = broker.getOrderService();
        var userService = broker.getUserService();
        var marketDataService = broker.getMarketDataService();

        var lastPricesResponse = marketDataService.getLastPrices(GetLastPricesRequest.of(instrumentConfig.getUid()));

        List<LastPrice> lastPrices = lastPricesResponse.getLastPrices();
        LastPrice lastPrice = lastPrices.get(lastPrices.size() - 1);

        var account = userService.openAccount(
            "testAccount",
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );

        PostOrderResponse postResponse = ordersService.post(new PostOrderRequest()
            .setInstrumentId(instrumentConfig.getUid())
            .setQuantity(10)
            .setPrice(lastPrice.getPrice())
            .setAccountId(account.getId())
            .setDirection(OrderDirection.BUY)
            .setOrderType(OrderType.LIMIT)
        );

        // Assert that the order was successfully posted
        assertNotNull(postResponse);
        assertNotNull(postResponse.getOrderId());
        assertEquals(0, postResponse.getLotsExecuted());
        assertEquals(10, postResponse.getLotsRequested());
        assertEquals(OrderDirection.BUY, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(Quotation.ZERO, postResponse.getTotalOrderAmount().getQuotation());
        assertEquals(Quotation.ZERO, postResponse.getExecutedOrderPrice().getQuotation());
        assertEquals(lastPrice.getPrice().multiply(10), postResponse.getInitialOrderPrice().getQuotation());
        assertEquals(lastPrice.getPrice(), postResponse.getInitialSecurityPrice().getQuotation());
        assertEquals(ExecutionStatus.NEW, postResponse.getExecutionStatus());

        Quotation commissionRatio = Quotation.of(config.getOrderService().getCommissionRatio());
        Quotation actualPrice = lastPrice.getPrice()
            .add(lastPrice.getPrice().multiply(commissionRatio))
            .multiply(10);

        // TODO: Finish this test
    }

    @Test
    void testSimulation() throws IOException {
        var mockStrategy = orderServiceTestingStrategy();

        // TODO: Finish this test

        mockStrategy.start(broker.context);

        simulator.run(
            Instant.parse("2020-12-30T07:00:00Z"),
            Instant.parse("2020-12-30T15:44:00Z")
        );
    }

    @AfterEach
    public void setDown() {
        candleStorageHandler.close();
    }

    StrategyInterface orderServiceTestingStrategy() {
        return new StrategyInterface() {
            String id;

            @Override
            public void start(AbstractContext<?> context) {

            }

            @Override
            public void run(AbstractContext<?> context) {
                var broker = context.getBrokerContainer().get("mock");

                context.getScheduler();

            }

            @Override
            public void stop(AbstractContext<?> context) {

            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public void setId(String id) {
                this.id = id;
            }
        };
    }
}
