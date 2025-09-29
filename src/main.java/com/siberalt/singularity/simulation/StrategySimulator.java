package com.siberalt.singularity.simulation;

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
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.InstrumentRepository;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.OrderSeriesProvider;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.ImportantLevelsUpsideCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class StrategySimulator {
    private final StrategyInterface strategy;
    private final OrderRepository orderRepository;
    private final ReadCandleRepository candleRepository;
    private final InstrumentRepository instrumentRepository;

    public StrategySimulator(
        StrategyInterface strategy,
        OrderRepository orderRepository,
        ReadCandleRepository candleRepository,
        InstrumentRepository instrumentRepository
    ) {
        this.strategy = strategy;
        this.orderRepository = orderRepository;
        this.candleRepository = candleRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public void simulate(Instant startTime, Instant endTime) throws AbstractException {
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

        UpsideCalculator upsideCalculator = ImportantLevelsUpsideCalculator.createLinear(
            5, 0.003
        );

        BasicTradeStrategy strategy = new BasicTradeStrategy(
            broker,
            "TMOS",
            account.getId(),
            upsideCalculator,
            candleRepository
        );

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

        Quotation profitPercent = profit.divide(initialInvestment).multiply(100);

        System.out.println("Period days: " + Duration.between(startTime, endTime).toDays());
        System.out.println("Total profit: " + profit);
        System.out.printf("Total profit percent: %.2f%%\n", profitPercent.toBigDecimal().doubleValue());
        System.out.println("Total orders: " + orders.size());
        System.out.println("Initial investment: " + initialInvestment);
        System.out.println("Final profit: " + profit.add(initialInvestment));
        BigDecimal apy = profitPercent
            .divide(BigDecimal.valueOf(Duration.between(startTime, endTime).toDays()), RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(365L));

        System.out.printf("APY: %.2f%%", apy);

        drawOrdersChart(orders, candleRepository, "TMOS", startTime, endTime);
    }

    private static void drawOrdersChart(
        List<Order> orders,
        ReadCandleRepository candleRepository,
        String instrumentUid,
        Instant startTime,
        Instant endTime
    ) {
        List<Candle> candles = candleRepository.getPeriod(instrumentUid, startTime, endTime);
        OrderSeriesProvider orderSeriesProvider = new OrderSeriesProvider(orders, candles)
            .setIncludeOutOfRangeOrders(true);

        PriceChart priceChart = new PriceChart(
            candleRepository,
            instrumentUid,
            c -> c.getClosePrice().toBigDecimal().doubleValue()
        );
        priceChart.setStepInterval(10);
        priceChart.addSeriesProvider(orderSeriesProvider);
        priceChart.render(candles);
    }
}
