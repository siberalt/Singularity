import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCurrentPriceResponse;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.broker.shared.BrokerFacade;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.InstrumentRepository;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;
import com.siberalt.singularity.presenter.google.series.OrderSeriesProvider;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.extreme.BaseExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ConcurrentFrameExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.cache.CachingExtremeLocator;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.ClusterLevelDetector;
import com.siberalt.singularity.strategy.level.selector.*;
import com.siberalt.singularity.strategy.level.track.LevelDetectorTracker;
import com.siberalt.singularity.strategy.level.track.LevelsSnapshot;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.upside.CompositeFactorUpsideCalculator;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.WindowUpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.*;
import com.siberalt.singularity.strategy.upside.volume.MFIUpsideCalculator;
import com.siberalt.singularity.strategy.upside.volume.VPTUpsideCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class BasicTradeStrategySimulation {
    public static void main(String[] args) throws AbstractException {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();
        OrderRepository orderRepository = new InMemoryOrderRepository();

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
        InstrumentRepository instrumentRepository = new InMemoryInstrumentRepository();
        instrumentRepository.save(
            new Instrument()
                .setInstrumentType(InstrumentType.SHARE)
                .setLot(1)
                .setIsin("RU102")
                .setCurrency("RUB")
                .setUid("TMOS")
                .setPositionUid("TMOS_POS")
        );
        SimulationClock clock = new SimpleSimulationClock();
        EventMockBroker broker = new EventMockBroker(
            candleRepository,
            instrumentRepository,
            orderRepository,
            clock
        );
        EventSimulator simulator = new EventSimulator(clock);
        Account account = broker.getUserService().openAccount(
            "Account",
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );

        Quotation initialInvestment = Quotation.of(1000000.00);
        broker.getOperationsService().addMoney(account.getId(), Money.of("RUB", initialInvestment));

        int tradePeriodCandles = 80;
        ExtremeLocator maximumLocator = createExtremeLocator(tradePeriodCandles, BaseExtremeLocator.createMaxLocator());
        ExtremeLocator minimumLocator = createExtremeLocator(tradePeriodCandles, BaseExtremeLocator.createMinLocator());
        LevelDetectorTracker supportTracker = createLevelDetector(0.005, minimumLocator);
        LevelDetectorTracker resistanceTracker = createLevelDetector(0.005, maximumLocator);
        LevelSelectorTracker selectorTracker = new LevelSelectorTracker(
            new ExtremeBasedLevelSelector(minimumLocator, maximumLocator)
        );
        StrategyInterface strategy = createLevelsStrategy(
            candleRepository,
            broker,
            account,
            supportTracker,
            resistanceTracker,
            selectorTracker
        );

        Observer observer = new Observer();
        simulator.addSimulationUnit(broker.getOrderService());
        simulator.addSimulationUnit(broker.getSubscriptionManager());
        simulator.addInitializableUnit((from, to) -> strategy.run(observer));

        simulator.run(startTime, endTime);
        System.out.println("Simulation completed.");
        Quotation profit = Quotation.ZERO;

        System.out.println("----------------------------");

        List<Order> orders = orderRepository.getByAccountId(account.getId())
            .stream()
            .sorted(Comparator.comparing(Order::getCreatedTime)).toList();

        for (Order order : orders) {
            System.out.println("Order ID: " + order.getId());
            System.out.println("Direction: " + order.getDirection());
            System.out.println("Instrument: " + order.getInstrument().getUid());
            System.out.println("Status: " + order.getExecutionStatus());
            System.out.println("Price: " + order.getBalanceChange());
            System.out.println("Quantity: " + order.getLotsExecuted());
            System.out.println("Created at: " + order.getCreatedTime());
            System.out.println("----------------------------");

            profit = profit.add(order.getBalanceChange());
        }

        BrokerFacade brokerFacade = BrokerFacade.of(broker);

        long instrumentCount = brokerFacade.getPositionSize(account.getId(), "TMOS");
        clock.syncCurrentTime(endTime);
        GetCurrentPriceResponse response = broker.getMarketDataService()
            .getCurrentPrice(new GetCurrentPriceRequest("TMOS"));
        Quotation instrumentPrice = response.getPrice();

        profit = profit.add(instrumentPrice.multiply(instrumentCount));
        Quotation profitPercent = profit.divide(initialInvestment).multiply(100);

        System.out.println("Period days: " + Duration.between(startTime, endTime).toDays());
        System.out.printf("Absolute profit: %.2f\n", profit.toDouble());
        System.out.printf("Total profit percent: %.2f%%\n", profitPercent.toDouble());
        System.out.println("Total orders: " + orders.size());
        System.out.printf("Initial investment: %.2f\n", initialInvestment.toDouble());
        System.out.printf("Result balance: %.2f\n", profit.add(initialInvestment).toDouble());
        System.out.println("Instrument position size: " + instrumentCount + " (price: " + instrumentPrice + ")");
        BigDecimal apy = profitPercent
            .divide(BigDecimal.valueOf(Duration.between(startTime, endTime).toDays()), RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(365L));

        System.out.printf("APY: %.2f%%", apy);

        drawOrdersChart(
            supportTracker.getSnapshots(),
            resistanceTracker.getSnapshots(),
            orders,
            candleRepository,
            "TMOS",
            startTime,
            endTime,
            selectorTracker.getTrackedLevelPairs()
        );
    }

    private static StrategyInterface createLevelsStrategy(
        ReadCandleRepository candleRepository,
        EventSubscriptionBroker broker,
        Account account,
        LevelDetector supportDetector,
        LevelDetector resistanceDetector,
        LevelSelectorTracker selectorTracker
    ){
        KeyLevelsUpsideCalculator upsideCalculator = new KeyLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            //new ChannelLevelBasedUpsideCalculator()
            new SimpleLevelBasedUpsideCalculator()
        );

        upsideCalculator.setLevelSelector(selectorTracker);
        BasicTradeStrategy strategy = new BasicTradeStrategy(
            broker,
            "TMOS",
            account.getId(),
            new WindowUpsideCalculator(upsideCalculator, 10080),
            candleRepository
        );
        strategy.setBuyThreshold(0.4);
        strategy.setSellThreshold(-0.5);
        strategy.setStep(40);

        return strategy;
    }

    private static ExtremeLocator createExtremeLocator(int frameSize, ExtremeLocator baseLocator) {
        return new CachingExtremeLocator(
            ConcurrentFrameExtremeLocator.builder(baseLocator)
                .setFrameSize(frameSize)
                .setExtremeVicinity(15)
                .build()
        );
    }

    private static StrategyInterface createStrategy(
        ReadCandleRepository candleRepository,
        EventSubscriptionBroker broker,
        Account account,
        LevelDetector supportDetector,
        LevelDetector resistanceDetector,
        LevelSelectorTracker selectorTracker
    ) {
        UpsideCalculator compositeUpsideCalculator = new CompositeFactorUpsideCalculator(
            List.of(
                new CompositeFactorUpsideCalculator.WeightedCalculator(new MFIUpsideCalculator(), 0.6),
                new CompositeFactorUpsideCalculator.WeightedCalculator(new VPTUpsideCalculator(), 0.4)
            )
        );

        LevelBasedUpsideCalculator adaptiveUpsideCalculator = new AdaptiveUpsideCalculator(
            new BasicLevelBasedUpsideCalculator(-0.3, 0.3),
            compositeUpsideCalculator
        );

        KeyLevelsUpsideCalculator upsideCalculator = new KeyLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            adaptiveUpsideCalculator
        );
        upsideCalculator.setLevelSelector(selectorTracker);

        BasicTradeStrategy strategy = new BasicTradeStrategy(
            broker,
            "TMOS",
            account.getId(),
            upsideCalculator,
            candleRepository
        );
        strategy.setBuyThreshold(0.4);
        strategy.setSellThreshold(-0.5);
        strategy.setStep(80);

        return strategy;
    }

    private static void drawOrdersChart(
        List<LevelsSnapshot> supportLevelsSnapshots,
        List<LevelsSnapshot> resistanceLevelsSnapshots,
        List<Order> orders,
        ReadCandleRepository candleRepository,
        String instrumentUid,
        Instant startTime,
        Instant endTime,
        List<LevelPairsSnapshot> levelPairsSnapshots
    ) {
        List<Candle> candles = candleRepository.getPeriod(instrumentUid, startTime, endTime);
        OrderSeriesProvider orderSeriesProvider = new OrderSeriesProvider(orders, candles)
            .setBuyPointsSize(4)
            .setSellPointsSize(4)
            .setIncludeOutOfRangeOrders(true);

        PriceChart priceChart = new PriceChart(
            candleRepository,
            instrumentUid,
            Candle::getTypicalPriceAsDouble
        );
        priceChart.addSeriesProvider(orderSeriesProvider);
        List<List<Level<Double>>> selectedSupportLevels = levelPairsSnapshots.stream()
            .map(snapshot -> snapshot.levelPairs().stream().map(LevelPair::support).toList())
            .toList();
        List<List<Level<Double>>> selectedResistanceLevels = levelPairsSnapshots.stream()
            .map(snapshot -> snapshot.levelPairs().stream().map(LevelPair::resistance).toList())
            .toList();

        addLevelsToChart(
            priceChart,
            "Support",
            supportLevelsSnapshots,
            "#00FFFA",
            selectedSupportLevels,
            "#008B8B"
        );
        addLevelsToChart(
            priceChart,
            "Resistance",
            resistanceLevelsSnapshots,
            "#FFBB00",
            selectedResistanceLevels,
            "#CC8400"
        );
        priceChart.setStepInterval(3);
        priceChart.render(candles);
    }

    private static LevelDetectorTracker createLevelDetector(double sensitivity, ExtremeLocator baseLocator) {
        ClusterLevelDetector levelDetector = new ClusterLevelDetector(sensitivity, baseLocator);

        return new LevelDetectorTracker(
//            LinearLevelDetector.createSupport(
//                tradePeriodCandles, 0.003
//            )
            levelDetector
        );
    }

    private static void addLevelsToChart(
        PriceChart chart,
        String name,
        List<LevelsSnapshot> levelsSnapshots,
        String color,
        List<List<Level<Double>>> selectedLevels,
        String selectedColor
    ) {
        var levelsProvider = new FunctionGroupSeriesProvider(name);
        var selectedLevelsProvider = new FunctionGroupSeriesProvider(name + " Selected");

        Iterator<Map<String, Level<Double>>> iterator = selectedLevels.stream()
            .map(levels -> levels.stream().collect(Collectors.toMap(Level::id, level -> level)))
            .iterator();

        for (LevelsSnapshot snapshot : levelsSnapshots) {
            long indexFrom = snapshot.fromIndex();
            long indexTo = snapshot.toIndex();
            Map<String, Level<Double>> currentSelectedLevels = iterator.hasNext()
                ? iterator.next()
                : Collections.emptyMap();

            for (Level<Double> level : snapshot.levels()) {
                if (currentSelectedLevels.containsKey(level.id())) {
                    selectedLevelsProvider.addFunction(indexFrom, indexTo, level.function());
                } else {
                    levelsProvider.addFunction(indexFrom, indexTo, level.function());
                }
            }
        }
        levelsProvider.setColor(color);
        selectedLevelsProvider.setColor(selectedColor);
        chart.addSeriesProvider(levelsProvider);
        chart.addSeriesProvider(selectedLevelsProvider);
    }
}
