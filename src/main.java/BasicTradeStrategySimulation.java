import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
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
import com.siberalt.singularity.presenter.google.VolumeChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;
import com.siberalt.singularity.presenter.google.series.OrderSeriesProvider;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.extreme.*;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.StatelessClusterLevelDetector;
import com.siberalt.singularity.strategy.level.selector.*;
import com.siberalt.singularity.strategy.level.track.*;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.upside.*;
import com.siberalt.singularity.strategy.upside.extreme.MaximinUpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.*;
import com.siberalt.singularity.strategy.upside.level.adaptive.AdaptiveUpsideCalculator;
import com.siberalt.singularity.strategy.upside.subrange.CalendarPeriodFilterDecorator;
import com.siberalt.singularity.strategy.upside.volume.VWAPUpsideCalculator;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class BasicTradeStrategySimulation {
    public static void main(String[] args) throws AbstractException {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();
        OrderRepository orderRepository = new InMemoryOrderRepository();
        boolean enableTracing = false;

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
        broker.getOrderService().setCommissionRatio(0.0005);
        EventSimulator simulator = new EventSimulator(clock);
        Account account = broker.getUserService().openAccount(
            "Account",
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );

        Quotation initialInvestment = Quotation.of(1000000.00);
        broker.getOperationsService().addMoney(account.getId(), Money.of("RUB", initialInvestment));

        ExtremeLocator maximumLocator = PivotPointExtremeLocator.ofMaximums(100);
        ExtremeLocator minimumLocator = PivotPointExtremeLocator.ofMinimums(100);
        LevelDetectorWindowTracker supportTracker = createLevelDetector(1.4, minimumLocator);
        LevelDetectorWindowTracker resistanceTracker = createLevelDetector(1.4, maximumLocator);
        var levelSelector = new StrongestLevelPairSelector(2);
        LevelPairSelectorWindowTracker selectorTracker = new LevelPairSelectorWindowTracker(levelSelector);
        StrategyInterface strategy = createLevelsStrategy(
            candleRepository,
            broker,
            account,
            supportTracker,
            resistanceTracker,
            selectorTracker,
            maximumLocator,
            minimumLocator
        );

        Observer observer = new Observer();
        simulator.addSimulationUnit(broker.getOrderService());
        simulator.addSimulationUnit(broker.getSubscriptionManager());
        simulator.addInitializableUnit((from, to) -> strategy.run(observer));

        Instant strategyBeginTime = Instant.now();
        simulator.run(startTime, endTime);

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
        }

        BrokerFacade brokerFacade = BrokerFacade.of(broker);

        long instrumentCount = brokerFacade.getPositionSize(account.getId(), "TMOS");
        clock.syncCurrentTime(endTime);

        if (instrumentCount > 0) {
            brokerFacade.sellMarket(account.getId(), "TMOS", instrumentCount);
        }

        Quotation balance = broker.getOperationsService().getAvailableMoney(account.getId(), "RUB").getQuotation();
        Quotation profit = balance.subtract(initialInvestment);
        Quotation profitPercent = profit.divide(initialInvestment).multiply(100);

        Duration duration = Duration.between(Instant.now(), strategyBeginTime);
        System.out.println("Simulation completed. Time elapsed: " + duration);
        System.out.println("Period days: " + Duration.between(startTime, endTime).toDays());
        System.out.printf("Absolute profit: %.2f\n", profit.toDouble());
        System.out.printf("Total profit percent: %.2f%%\n", profitPercent.toDouble());
        System.out.println("Total orders: " + orders.size());
        System.out.printf("Initial investment: %.2f\n", initialInvestment.toDouble());
        System.out.printf("Result balance: %.2f\n", balance.toDouble());
        BigDecimal apy = profitPercent
            .divide(BigDecimal.valueOf(Duration.between(startTime, endTime).toDays()), RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(365L));

        System.out.printf("APY: %.2f%%", apy);

        if (!enableTracing) {
            drawOrdersChart(
                List.of(),
                List.of(),
                orders,
                candleRepository,
                "TMOS",
                startTime,
                endTime,
                List.of()
            );
        } else {
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
    }

    private static StrategyInterface createLevelsStrategy(
        ReadCandleRepository candleRepository,
        EventSubscriptionBroker broker,
        Account account,
        LevelDetector supportDetector,
        LevelDetector resistanceDetector,
        LevelPairSelector selectorTracker,
        ExtremeLocator maximaBaseLocator,
        ExtremeLocator minimaBaseLocator
    ) {
//        UpsideCalculator volumeUpsideCalculator = new CompositeFactorUpsideCalculator(
//            List.of(
//                //new CompositeFactorUpsideCalculator.WeightedCalculator(new NetVolumeUpsideCalculator(), 0.7),
//                new CompositeFactorUpsideCalculator.WeightedCalculator(new VPTUpsideCalculator(), 1)
//            )
//        );
//
//        LevelBasedUpsideCalculator adaptiveUpsideCalculator = new AdaptiveUpsideCalculator(
//            new ChannelLevelBasedUpsideCalculator(),
//            SubrangeUpsideCalculator.ofLastN(60, volumeUpsideCalculator)
//        );
//
//        upsideCalculator.setLevelSelector(selectorTracker);
        var maximinUpsideCalculator = new MaximinUpsideCalculator(
            LastExtremeLocator.ofMaximums(10, Candle::getCloseAsDouble),
            LastExtremeLocator.ofMinimums(10, Candle::getCloseAsDouble)
        );
        var volumeUpsideCalculator = new CompositeFactorUpsideCalculator(
            List.of(
//                CompositeFactorUpsideCalculator.newWeightedCalculator(
//                    SubrangeUpsideCalculator.ofLastN(60, new VWAPUpsideCalculator()), 1
//                )
                CompositeFactorUpsideCalculator.newWeightedCalculator(
                    CalendarPeriodFilterDecorator.ofLastDays(1, new VWAPUpsideCalculator()), 1
                )
            )
        );

        var adaptiveUpsideCalculator = new AdaptiveUpsideCalculator(
            new SimpleLevelBasedUpsideCalculator(),
            volumeUpsideCalculator
        );

        UpsideCalculator levelUpsideCalculator = new KeyLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            adaptiveUpsideCalculator,
            selectorTracker,
            volumeUpsideCalculator
        );
        //levelUpsideCalculator = SubrangeUpsideCalculator.ofLastN(60 * 24 * 7 * 2, levelUpsideCalculator);

        CompositeFactorUpsideCalculator compositeUpsideCalculator = new CompositeFactorUpsideCalculator(
            List.of(
                // CompositeFactorUpsideCalculator.newWeightedCalculator(levelUpsideCalculator, 0.8),
                CompositeFactorUpsideCalculator.newWeightedCalculator(SubrangeUpsideCalculator.ofLastN(60 * 24, maximinUpsideCalculator), 0.2),
                CompositeFactorUpsideCalculator.newWeightedCalculator(volumeUpsideCalculator, 0.8)

                // CompositeFactorUpsideCalculator.newWeightedCalculator(maximinUpsideCalculator, 0.2)
                //new CompositeFactorUpsideCalculator.WeightedCalculator(subrangeUpsideCalculator, 0.05)
            )
        );
        BasicTradeStrategy strategy = new BasicTradeStrategy(
            broker,
            "TMOS",
            account.getId(),
            new WindowUpsideCalculator(compositeUpsideCalculator, 60 * 24 * 7 * 2),
            candleRepository
        );
        strategy.setLookbackCandles(60 * 24 * 7 * 2);
        strategy.setBuyThreshold(0.5);
        strategy.setSellThreshold(-0.5);
        strategy.setStep(10);

        return strategy;
    }

    private static void drawOrdersChart(
        List<SnapshotLevelGroup> supportLevelsSnapshots,
        List<SnapshotLevelGroup> resistanceLevelsSnapshots,
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
            Candle::getCloseAsDouble
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
        priceChart.setStepInterval(1);
        priceChart.render(candles);
        VolumeChart volumeChart = new VolumeChart(1);
        volumeChart.render(candles);
        Toolkit.getDefaultToolkit().beep();
    }

    private static LevelDetectorWindowTracker createLevelDetector(double multiplier, ExtremeLocator baseLocator) {
        StatelessClusterLevelDetector levelDetector = StatelessClusterLevelDetector.createDefault(multiplier, baseLocator);

        return new LevelDetectorWindowTracker(
//            LinearLevelDetector.createSupport(
//                tradePeriodCandles, 0.003
//            )
            levelDetector
        );
    }

    private static void addLevelsToChart(
        PriceChart chart,
        String name,
        List<SnapshotLevelGroup> levelsSnapshots,
        String color,
        List<List<Level<Double>>> selectedLevels,
        String selectedColor
    ) {
        FunctionGroupSeriesProvider levelsProvider = new FunctionGroupSeriesProvider(name);
        FunctionGroupSeriesProvider selectedLevelsProvider = new FunctionGroupSeriesProvider(name + " Selected");

        Iterator<Map<String, Level<Double>>> iterator = selectedLevels.stream()
            .map(
                levels -> levels.stream().collect(
                    Collectors.toMap(Level::id, level -> level, (existing, replacement) -> existing)
                )
            )
            .iterator();

        List<SnapshotLevelGroup> unselectedSnapshots = new ArrayList<>();
        List<SnapshotLevelGroup> selectedSnapshots = new ArrayList<>();

        for (SnapshotLevelGroup snapshot : levelsSnapshots) {
            Map<String, Level<Double>> currentSelectedLevels = iterator.hasNext()
                ? iterator.next()
                : Collections.emptyMap();

            List<Level<Double>> snapshotSelectedLevels = new ArrayList<>();
            List<Level<Double>> snapshotUnselectedLevels = new ArrayList<>();

            for (Level<Double> level : snapshot.levels()) {
                if (currentSelectedLevels.containsKey(level.id())) {
                    snapshotSelectedLevels.add(level);
                } else {
                    snapshotUnselectedLevels.add(level);
                }
            }

            if (!snapshotUnselectedLevels.isEmpty()) {
                unselectedSnapshots.add(
                    new SnapshotLevelGroup(
                        snapshot.fromPoint(),
                        snapshot.toPoint(),
                        snapshotUnselectedLevels
                    )
                );
            }

            if (!snapshotSelectedLevels.isEmpty()) {
                selectedSnapshots.add(
                    new SnapshotLevelGroup(
                        snapshot.fromPoint(),
                        snapshot.toPoint(),
                        snapshotSelectedLevels
                    )
                );
            }
        }

        LevelTracer levelTracer = new LevelTracer(0.002);
        flushLevelsToSeriesProvider(levelTracer.trace(unselectedSnapshots), levelsProvider);
        flushLevelsToSeriesProvider(levelTracer.trace(selectedSnapshots), selectedLevelsProvider);

        levelsProvider.setColor(color);
        selectedLevelsProvider.setColor(selectedColor);
        chart.addSeriesProvider(levelsProvider);
        chart.addSeriesProvider(selectedLevelsProvider);
    }

    private static void flushLevelsToSeriesProvider(
        LevelTraceGroup levelTraceGroup,
        FunctionGroupSeriesProvider seriesProvider
    ) {
        for (LevelTrace levelTrace : levelTraceGroup.levelTraces()) {
            seriesProvider.addFunction(
                levelTrace.fromPoint().index(),
                levelTrace.toPoint().index(),
                levelTrace.function()
            );
        }
    }
}
