package investtech.broker.impl.simulation;

import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.impl.mock.MockBroker;
import investtech.broker.impl.mock.MockOrderService;
import investtech.broker.impl.mock.config.MockBrokerConfig;
import investtech.simulation.Simulator;
import investtech.simulation.shared.instrument.RuntimeInstrumentStorage;
import investtech.simulation.shared.market.candle.factory.CvsFileCandleStorageFactory;
import investtech.simulation.shared.market.candle.storage.cvs.CvsCandleStorage;
import investtech.strategy.StrategyInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.emulation.time.SimulationTimeSynchronizer;
import investtech.test.util.ConfigLoader;
import investtech.test.util.resource.ResourceHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

public class SimulationOrderServiceIT {
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

//        timeSynchronizer = new SimulationTimeSynchronizer();
//        EventObserver eventObserver = new EventObserver();
//        SimulationScheduler scheduler = new SimulationScheduler();
//        broker = new MockBroker(candleStorageHandler.create(), instrumentStorage);
//        SimulationContext context = new SimulationContext(
//            scheduler,
//            new SimulationBrokerContainer(Map.of(config.getBrokerId(), broker)),
//            timeSynchronizer
//        );
//        orderService = broker.getOrderService();
//
//        broker.applyContext(context);
//        scheduler.applyContext(context);
//
//        scheduler.observeEventsBy(eventObserver);
//        orderService.observeEventsBy(eventObserver);
//        orderService.setOrderCommissionRatio(config.getOrderService().getCommissionRatio());
//
//        simulator = new Simulator(eventObserver, timeSynchronizer);
    }

    @Test
    void testSimulation() throws IOException {
        var mockStrategy = orderServiceTestingStrategy();

        // TODO: Finish this test

//        mockStrategy.start(broker.context);

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
