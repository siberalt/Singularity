import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.broker.impl.mock.LimitTrigger;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository;
import com.siberalt.singularity.entity.operation.InMemoryOperationRepository;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.impl.RsiLimitEntryStrategy;
import com.siberalt.singularity.strategy.market.position.BaseEntryPriceCalculator;
import com.siberalt.singularity.strategy.simulation.runner.StrategyBacktester;
import com.siberalt.singularity.strategy.simulation.runner.StrategyResult;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The RSI limit entry run through the event simulator, one instrument at a time.
 * <p>
 * The measurements that led here were of returns per signal; this is the same idea as a trading
 * account would live it - money that is spent while a position is held, a commission on each side,
 * the exit crossing the spread, and an order that fills only in a minute that closes under its limit
 * ({@link LimitTrigger#CLOSE_THROUGH}). Each instrument gets its own account, so the rows are not a
 * portfolio: they say what one instrument traded this way would have made.
 */
public class RsiLimitEntrySimulation {
    private static final long[] INSTRUMENTS = {4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
    private static final Instant FROM = Instant.parse("2023-01-03T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");
    /** How far under the signal hour's close the limit goes, in hourly ATRs. */
    private static final double LIMIT_OFFSET = 0.5;
    /** Per side, as T-Bank charges on the investor tariff. */
    private static final double COMMISSION = 0.0005;
    private static final Money INITIAL = Money.of("RUB", 1_000_000.00);

    public static void main(String[] args) throws Exception {
        ConfigInterface configuration = new YamlConfig(Files.newInputStream(Paths.get("src/main/resources/app.yaml")));
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candles = new SqliteCandleRepositoryFactory().create(dbPath);
        SqliteInstrumentRepository instruments = new SqliteInstrumentRepository(DriverManager.getConnection(dbPath));
        // Named arguments: entry=market|<atr>, rsi=20, hold=5, exit=market|<atr>, fee=0.0005, ids=4,5,7
        Map<String, String> options = new HashMap<>();

        for (String argument : args) {
            int at = argument.indexOf('=');
            options.put(argument.substring(0, at), argument.substring(at + 1));
        }

        long[] chosen = options.containsKey("ids")
            ? Arrays.stream(options.get("ids").split(",")).mapToLong(Long::parseLong).toArray()
            : INSTRUMENTS;
        Instant from = Instant.parse(options.getOrDefault("from", FROM.toString()));
        Instant to = Instant.parse(options.getOrDefault("to", TO.toString()));
        String entry = options.getOrDefault("entry", String.valueOf(LIMIT_OFFSET));
        boolean atMarket = entry.equals("market");
        double offset = atMarket ? 0 : Double.parseDouble(entry);
        String exit = options.getOrDefault("exit", "market");
        double exitOffset = exit.equals("market") ? 0 : Double.parseDouble(exit);
        double oversold = Double.parseDouble(options.getOrDefault("rsi", "20"));
        int hold = Integer.parseInt(options.getOrDefault("hold", "5"));
        double commission = Double.parseDouble(options.getOrDefault("fee", String.valueOf(COMMISSION)));

        System.out.printf(Locale.ROOT, "%s .. %s: RSI < %.0f, entry %s, exit %s, hold %d h, commission %.3f%% a side%n",
            from, to, oversold,
            atMarket ? "at the market" : String.format(Locale.ROOT, "limit %.2f ATR under", offset),
            exitOffset == 0 ? "at the market" : String.format(Locale.ROOT, "limit %.2f ATR over the fill", exitOffset),
            hold, 100 * commission);
        System.out.printf("%4s %10s %8s %7s %9s %9s %8s%n", "id", "profit %", "trades", "wins", "mean %", "median %", "time");

        List<Double> profits = new ArrayList<>();
        List<Double> allTrades = new ArrayList<>();

        for (long id : chosen) {
            String uid = instruments.brokerInstrumentIdOf(AbstractTinkoffBroker.ID, id).orElseThrow();
            InMemoryInstrumentRepository listing = new InMemoryInstrumentRepository();

            listing.save(EventMockBroker.DEFAULT_ID, new Instrument()
                .setInstrumentType(InstrumentType.SHARE)
                .setLot(1)
                .setCurrency("RUB")
                .setUid(uid));

            InMemoryOperationRepository operations = new InMemoryOperationRepository();
            SimpleSimulationClock clock = new SimpleSimulationClock();
            EventMockBroker broker = EventMockBroker.builder(candles, instruments, listing, new InMemoryOrderRepository(),
                    operations, clock)
                .setCommissionRatio(commission)
                .build();

            broker.getOrderService().getPriceModel().setHalfSpreadRatio(0.00015).setSlippageImpactRatio(0.0006);
            // How much the idea earns, not how much money it can carry: a million roubles an order is
            // hours of minutes on the thinner names, and under a participation limit one trade would
            // still be filling when the next began.
            broker.getOrderService().getLiquidityModel().setInfiniteLiquidity(true);
            broker.getPendingOrderHandler().setLimitTrigger(LimitTrigger.CLOSE_THROUGH);

            StrategyResult result = new StrategyBacktester<EventMockBroker>(
                (range, accountId, simulated, observer) -> {
                    RsiLimitEntryStrategy strategy = new RsiLimitEntryStrategy(simulated, uid, accountId, candles)
                        .setLimitOffset(offset)
                        .setEntryAtMarket(atMarket)
                        .setOversold(oversold)
                        .setHoldHours(hold);

                    if (exitOffset > 0) {
                        strategy.setExitLimit(new BaseEntryPriceCalculator(operations), exitOffset);
                    }

                    strategy.run(observer);
                },
                broker,
                uid,
                INITIAL,
                clock
            ).run(from, to);

            List<Double> trades = tradesOf(operations.getByAccountId(result.accountId(), new TimeRange(from, to)));
            List<Double> sorted = trades.stream().sorted().toList();

            profits.add(result.profitPercent());
            allTrades.addAll(trades);

            System.out.printf(Locale.ROOT, "%4d %+10.2f %8d %6.0f%% %+9.3f %+9.3f %7ds%n", id, result.profitPercent(),
                trades.size(), 100.0 * trades.stream().filter(trade -> trade > 0).count() / Math.max(1, trades.size()),
                trades.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2), result.executionDuration().toSeconds());
        }

        System.out.printf(Locale.ROOT, "%nmean profit per instrument %+.2f%%, positive on %d of %d; %d trades, mean %+.3f%% a trade%n",
            profits.stream().mapToDouble(Double::doubleValue).average().orElse(0),
            profits.stream().filter(profit -> profit > 0).count(), profits.size(), allTrades.size(),
            allTrades.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    /**
     * Each trade's result in per cent of what it spent: every executed payment - buys, fees, the sale -
     * summed until a sale closes the trade. The sale's own fee is journalled beside it at the same
     * moment, in either order, so a trade closes only once the moment of its sale has passed.
     */
    static List<Double> tradesOf(List<Operation> operations) {
        List<Double> trades = new ArrayList<>();
        double spent = 0;
        double net = 0;
        Instant soldAt = null;

        for (Operation operation : operations.stream()
            .filter(operation -> operation.state() == OperationState.EXECUTED)
            .sorted(Comparator.comparing(Operation::executedDate))
            .toList()) {
            if (soldAt != null && !operation.executedDate().equals(soldAt)) {
                trades.add(100 * net / spent);
                spent = 0;
                net = 0;
                soldAt = null;
            }

            double payment = operation.payment() == null ? 0 : operation.payment().toDouble();

            if (operation.direction() == OperationType.BUY) {
                spent += Math.abs(payment);
            }

            net += payment;

            if (operation.direction() == OperationType.SELL && spent > 0) {
                soldAt = operation.executedDate();
            }
        }

        if (soldAt != null) {
            trades.add(100 * net / spent);
        }

        return trades;
    }
}
