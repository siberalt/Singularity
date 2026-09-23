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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The same strategy as {@link RsiLimitEntrySimulation}, but all the instruments on one account.
 * <p>
 * Why it is worth a run of its own. Per instrument the strategy holds a position for well under one
 * per cent of the trading hours, so a row saying it made two per cent over two years says almost
 * nothing about what the money earned - the money was idle nearly all of it. Thirty-three instruments
 * on one account rarely want it at the same moment, so the same trades can be paid for out of the same
 * capital, and what they come to per rouble is the question this answers.
 * <p>
 * The signals compete: each trade takes {@code share} of whatever money is free at that moment, so the
 * first of a busy day is the largest and nothing is ever refused for want of funds. Everything else -
 * the fills, the costs, the market-neutral measure - is as in the per-instrument run.
 */
public class RsiPortfolioSimulation {
    private static final long[] INSTRUMENTS = {4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
    private static final Instant FROM = Instant.parse("2023-01-03T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");
    private static final double COMMISSION = 0.0005;
    private static final Money INITIAL = Money.of("RUB", 1_000_000.00);

    public static void main(String[] args) throws Exception {
        ConfigInterface configuration = new YamlConfig(Files.newInputStream(Paths.get("src/main/resources/app.yaml")));
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candles = new SqliteCandleRepositoryFactory().create(dbPath);
        SqliteInstrumentRepository instruments = new SqliteInstrumentRepository(DriverManager.getConnection(dbPath));
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
        String entry = options.getOrDefault("entry", "1.0");
        boolean atMarket = entry.equals("market");
        double offset = atMarket ? 0 : Double.parseDouble(entry);
        String exit = options.getOrDefault("exit", "market");
        double exitOffset = exit.equals("market") ? 0 : Double.parseDouble(exit);
        double oversold = Double.parseDouble(options.getOrDefault("rsi", "20"));
        double exitRsi = Double.parseDouble(options.getOrDefault("exitRsi", "30"));
        int hold = Integer.parseInt(options.getOrDefault("hold", "40"));
        double share = Double.parseDouble(options.getOrDefault("share", "0.2"));
        double commission = Double.parseDouble(options.getOrDefault("fee", String.valueOf(COMMISSION)));

        // How many median hourly volumes the signal hour has to trade; zero asks nothing.
        double volume = Double.parseDouble(options.getOrDefault("vol", "0"));

        System.out.printf(Locale.ROOT,
            "%s .. %s: %d instruments on one account, %.0f%% of the free money a trade%n",
            from, to, chosen.length, 100 * share);
        System.out.printf(Locale.ROOT, "RSI < %.0f, entry %s, exit %s, out at RSI >= %.0f, hold at most %d h, commission %.3f%% a side%n",
            oversold, atMarket ? "at the market" : String.format(Locale.ROOT, "limit %.2f ATR under", offset),
            exitOffset == 0 ? "at the market" : String.format(Locale.ROOT, "limit %.2f ATR over the fill", exitOffset),
            exitRsi, hold, 100 * commission);

        InMemoryInstrumentRepository listing = new InMemoryInstrumentRepository();
        List<String> uids = new ArrayList<>();

        for (long id : chosen) {
            String uid = instruments.brokerInstrumentIdOf(AbstractTinkoffBroker.ID, id).orElseThrow();

            uids.add(uid);
            listing.save(EventMockBroker.DEFAULT_ID, new Instrument()
                .setInstrumentType(InstrumentType.SHARE)
                .setLot(1)
                .setCurrency("RUB")
                .setUid(uid));
        }

        InMemoryOperationRepository operations = new InMemoryOperationRepository();
        SimpleSimulationClock clock = new SimpleSimulationClock();
        EventMockBroker broker = EventMockBroker.builder(candles, instruments, listing,
                new InMemoryOrderRepository(), operations, clock)
            .setCommissionRatio(commission)
            .build();

        broker.getOrderService().getPriceModel().setHalfSpreadRatio(0.00015).setSlippageImpactRatio(0.0006);
        broker.getOrderService().getLiquidityModel().setInfiniteLiquidity(true);
        broker.getPendingOrderHandler().setLimitTrigger(LimitTrigger.CLOSE_THROUGH);

        StrategyResult result = new StrategyBacktester<EventMockBroker>(
            (range, accountId, simulated, observer) -> {
                for (String uid : uids) {
                    RsiLimitEntryStrategy strategy = new RsiLimitEntryStrategy(simulated, uid, accountId, candles)
                        .setLimitOffset(offset)
                        .setEntryAtMarket(atMarket)
                        .setOversold(oversold)
                        .setMinVolume(volume, 24)
                        .setHoldHours(hold)
                        .setExitRsi(exitRsi)
                        .setPositionShare(share);

                    if (exitOffset > 0) {
                        strategy.setExitLimit(new BaseEntryPriceCalculator(operations), exitOffset);
                    }

                    strategy.run(observer);
                }
            },
            broker,
            uids,
            INITIAL,
            clock
        ).run(from, to);

        report(result, operations.getByAccountId(result.accountId(), new TimeRange(from, to)),
            RsiLimitEntrySimulation.Market.of(candles, INSTRUMENTS, from, to), from, to);
    }

    /**
     * What the account did, and how busy it was doing it. Trades are read per instrument - one
     * instrument's buys and sells make a trade, and on one account they are interleaved with everyone
     * else's.
     */
    static void report(StrategyResult result, List<Operation> operations, RsiLimitEntrySimulation.Market market,
                       Instant from, Instant to) {
        Map<String, List<Operation>> byInstrument = new TreeMap<>();

        for (Operation operation : operations) {
            byInstrument.computeIfAbsent(operation.instrumentUid(), uid -> new ArrayList<>()).add(operation);
        }

        List<RsiLimitEntrySimulation.Trade> trades = new ArrayList<>();
        int traded = 0;

        for (List<Operation> mine : byInstrument.values()) {
            List<RsiLimitEntrySimulation.Trade> ours = RsiLimitEntrySimulation.tradesOf(mine);

            traded += ours.isEmpty() ? 0 : 1;
            trades.addAll(ours);
        }

        double years = Duration.between(from, to).toDays() / 365.0;

        System.out.printf(Locale.ROOT, "%nprofit %+.2f%%, %.2f%% a year, balance %.0f%n",
            result.profitPercent(), result.apy(), result.balance().getQuotation().toDouble());
        System.out.printf(Locale.ROOT, "%d trades on %d instruments, %.0f a year, mean %+.3f%% a trade, %+.3f%% over the market%n",
            trades.size(), traded, trades.size() / years,
            trades.stream().mapToDouble(RsiLimitEntrySimulation.Trade::percent).average().orElse(0),
            market.excessOf(trades));
        System.out.printf(Locale.ROOT, "in the market %.0f%% of the hours, at most %d positions at once, %.1f on average when open%n",
            100 * busyShare(trades, from, to), mostAtOnce(trades), meanWhenOpen(trades));
        System.out.printf(Locale.ROOT, "for comparison: holding the average instrument through the period %+.2f%%, %.2f%% a year%n",
            market.moveBetween(from, to), market.moveBetween(from, to) / years);
    }

    /** The share of the period's hours with at least one position open. */
    static double busyShare(List<RsiLimitEntrySimulation.Trade> trades, Instant from, Instant to) {
        long hours = Duration.between(from, to).toHours();

        return hours == 0 ? 0 : (double) hoursOpen(trades).size() / hours;
    }

    static int mostAtOnce(List<RsiLimitEntrySimulation.Trade> trades) {
        return hoursOpen(trades).values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    static double meanWhenOpen(List<RsiLimitEntrySimulation.Trade> trades) {
        return hoursOpen(trades).values().stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /** How many positions were open in each hour that had any. */
    private static Map<Long, Integer> hoursOpen(List<RsiLimitEntrySimulation.Trade> trades) {
        Map<Long, Integer> open = new HashMap<>();

        for (RsiLimitEntrySimulation.Trade trade : trades) {
            long entry = trade.entry().toEpochMilli() / 3_600_000;
            long exit = trade.exit().toEpochMilli() / 3_600_000;

            for (long hour = entry; hour <= exit; hour++) {
                open.merge(hour, 1, Integer::sum);
            }
        }

        return open;
    }
}
