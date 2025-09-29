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
import com.siberalt.singularity.presenter.google.series.LineSeriesProvider;
import com.siberalt.singularity.presenter.google.series.OrderSeriesProvider;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.level.linear.LinearLevel;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.BasicLevelBasedUpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.ImportantLevelsUpsideCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class BasicTradeStrategySimulation {
    public static void main(String[] args) throws AbstractException {
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

        Function<Candle, Double> priceExtractor = c -> c.getClosePrice().toBigDecimal().doubleValue();
        int tradePeriodCandles = 30;

        LinearLevelDetector supportDetector = createSupportDetector(
            tradePeriodCandles, priceExtractor
        );
        LinearLevelDetector resistanceDetector = createResistanceDetector(
            tradePeriodCandles, priceExtractor
        );

        UpsideCalculator upsideCalculator = new ImportantLevelsUpsideCalculator(
            supportDetector,
            resistanceDetector,
            new BasicLevelBasedUpsideCalculator()
        );
        BasicTradeStrategy strategy = new BasicTradeStrategy(
            broker,
            "TMOS",
            account.getId(),
            upsideCalculator,
            candleRepository
        );

        strategy.setTradePeriodCandles(tradePeriodCandles);

        Observer observer = new Observer();
        simulator.addSimulationUnit(broker.getOrderService());
        simulator.addSimulationUnit(broker.getSubscriptionManager());
        simulator.addInitializableUnit((from, to) -> {
            try {
                BrokerFacade.of(broker).buyBestPriceFullBalance(account.getId(), "TMOS");
            } catch (AbstractException e) {
                throw new RuntimeException(e);
            }

            strategy.run(observer);
        });

        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
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
        System.out.printf("Absolute profit: %.2f\n", profit.toBigDecimal().doubleValue());
        System.out.printf("Total profit percent: %.2f%%\n", profitPercent.toBigDecimal().doubleValue());
        System.out.println("Total orders: " + orders.size());
        System.out.printf("Initial investment: %.2f\n", initialInvestment.toBigDecimal().doubleValue());
        System.out.printf("Result balance: %.2f\n", profit.add(initialInvestment).toBigDecimal().doubleValue());
        System.out.println("Instrument position size: " + instrumentCount + " (price: " + instrumentPrice + ")");
        BigDecimal apy = profitPercent
            .divide(BigDecimal.valueOf(Duration.between(startTime, endTime).toDays()), RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(365L));

        System.out.printf("APY: %.2f%%", apy);

        drawOrdersChart(
            createSupportDetector(
                tradePeriodCandles, priceExtractor
            ),
            createResistanceDetector(
                tradePeriodCandles, priceExtractor
            ),
            orders,
            candleRepository,
            "TMOS",
            startTime,
            endTime
        );
    }

    private static void drawOrdersChart(
        LinearLevelDetector supportDetector,
        LinearLevelDetector resistanceDetector,
        List<Order> orders,
        ReadCandleRepository candleRepository,
        String instrumentUid,
        Instant startTime,
        Instant endTime
    ) {
        List<Candle> candles = candleRepository.getPeriod(instrumentUid, startTime, endTime);
        OrderSeriesProvider orderSeriesProvider = new OrderSeriesProvider(orders, candles)
            .setIncludeOutOfRangeOrders(true);

        List<LinearLevel<Double>> resultSupport = supportDetector.detect(candles);
        List<LinearLevel<Double>> resultResistance = resistanceDetector.detect(candles);

        PriceChart priceChart = new PriceChart(
            candleRepository,
            instrumentUid,
            c -> c.getClosePrice().toBigDecimal().doubleValue()
        );
        addLevelsToChart(priceChart, "Support", resultSupport, "#00FFFA");
        addLevelsToChart(priceChart, "Resistance", resultResistance, "#FFBB00");
        priceChart.setStepInterval(10);
        priceChart.addSeriesProvider(orderSeriesProvider);
        priceChart.render(candles);
    }

    private static LinearLevelDetector createSupportDetector(
        long tradePeriodCandles,
        Function<Candle, Double> priceExtractor
    ) {
        return LinearLevelDetector.createSupport(
            tradePeriodCandles, 0.003, priceExtractor, -0.001
        );
    }

    private static LinearLevelDetector createResistanceDetector(
        long tradePeriodCandles,
        Function<Candle, Double> priceExtractor
    ) {
        return LinearLevelDetector.createResistance(
            tradePeriodCandles, 0.003, priceExtractor, 0.001
        );
    }

    private static void addLevelsToChart(PriceChart chart, String name, List<LinearLevel<Double>> levels, String color) {
        var linesProvider = new LineSeriesProvider(name);
        levels.forEach(level -> linesProvider.addLine(level.getIndexFrom(), level.getIndexTo(), level.getFunction()));
        linesProvider.setColor(color);
        chart.addSeriesProvider(linesProvider);
    }
}
