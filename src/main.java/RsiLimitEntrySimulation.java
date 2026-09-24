import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.broker.impl.mock.LimitTrigger;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;
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
import java.util.NavigableMap;
import java.util.TreeMap;

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
        double exitRsi = Double.parseDouble(options.getOrDefault("exitRsi", "0"));
        int hold = Integer.parseInt(options.getOrDefault("hold", "5"));
        double commission = Double.parseDouble(options.getOrDefault("fee", String.valueOf(COMMISSION)));

        // How many median hourly volumes the signal hour has to trade; zero asks nothing.
        double volume = Double.parseDouble(options.getOrDefault("vol", "0"));
        CandleInterval interval = CandleInterval.valueOf(options.getOrDefault("bars", "HOUR"));

        System.out.printf(Locale.ROOT, "%s .. %s: RSI < %.0f, entry %s, exit %s, hold %d h, commission %.3f%% a side%n",
            from, to, oversold,
            atMarket ? "at the market" : String.format(Locale.ROOT, "limit %.2f ATR under", offset),
            exitOffset == 0 ? "at the market" : String.format(Locale.ROOT, "limit %.2f ATR over the fill", exitOffset),
            hold, 100 * commission);

        if (exitRsi > 0) {
            System.out.printf(Locale.ROOT, "  and out as soon as an hour closes with RSI >= %.0f%n", exitRsi);
        }

        Market market = Market.of(candles, INSTRUMENTS, from, to);

        System.out.printf("%4s %10s %8s %7s %9s %11s %9s%n",
            "id", "profit %", "trades", "wins", "mean %", "excess %", "in market");

        List<Double> profits = new ArrayList<>();
        List<Trade> allTrades = new ArrayList<>();

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
                        .setMinVolume(volume, 24)
                        .setInterval(interval)
                        .setHoldBars(hold)
                        .setExitRsi(exitRsi);

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

            List<Trade> trades = tradesOf(operations.getByAccountId(result.accountId(), new TimeRange(from, to)));

            profits.add(result.profitPercent());
            allTrades.addAll(trades);

            System.out.printf(Locale.ROOT, "%4d %+10.2f %8d %6.0f%% %+9.3f %+11.3f %8.1f%%%n", id, result.profitPercent(),
                trades.size(), 100.0 * trades.stream().filter(trade -> trade.percent() > 0).count() / Math.max(1, trades.size()),
                trades.stream().mapToDouble(Trade::percent).average().orElse(0),
                market.excessOf(trades), 100 * market.exposureOf(id, trades, from, to));
        }

        System.out.printf(Locale.ROOT, "%nmean profit per instrument %+.2f%%, positive on %d of %d%n",
            profits.stream().mapToDouble(Double::doubleValue).average().orElse(0),
            profits.stream().filter(profit -> profit > 0).count(), profits.size());
        System.out.printf(Locale.ROOT, "%d trades, mean %+.3f%% a trade, %+.3f%% over the market of the same hours%n",
            allTrades.size(), allTrades.stream().mapToDouble(Trade::percent).average().orElse(0),
            market.excessOf(allTrades));
    }

    /** One completed trade: when it was bought, when it was sold, and what it made of what it spent. */
    record Trade(Instant entry, Instant exit, double percent) {
    }

    /**
     * What every instrument did, hour by hour, so that a trade can be read against the market rather than
     * on its own.
     * <p>
     * The simulation trades one instrument at a time, and its profit therefore carries whatever the market
     * did while the position was open - the longer the holding time, the more of it. Every measurement
     * before this one was market-neutral, and this is the same measure: the mean move of all the
     * instruments over the very hours a trade was held, subtracted from what the trade made.
     */
    record Market(Map<Long, NavigableMap<Long, Double>> byInstrument) {
        static Market of(SqliteCandleRepository candles, long[] ids, Instant from, Instant to) {
            Map<Long, NavigableMap<Long, Double>> byInstrument = new HashMap<>();
            CandleAggregator aggregator = new CandleAggregator();

            for (long id : ids) {
                NavigableMap<Long, Double> hours = new TreeMap<>();

                for (Candle bar : aggregator.aggregate(candles.getPeriod(id, from, to), CandleInterval.HOUR)) {
                    hours.put(aggregator.bucketOf(bar, CandleInterval.HOUR), bar.getCloseAsDouble());
                }

                if (!hours.isEmpty()) {
                    byInstrument.put(id, hours);
                }
            }

            return new Market(byInstrument);
        }

        /** The mean of the trades' results less the market's move over each trade's own hours, in per cent. */
        double excessOf(List<Trade> trades) {
            double total = 0;
            int counted = 0;

            for (Trade trade : trades) {
                double market = moveBetween(trade.entry(), trade.exit());

                if (!Double.isNaN(market)) {
                    total += trade.percent() - market;
                    counted++;
                }
            }

            return counted == 0 ? 0 : total / counted;
        }

        /** The share of the instrument's trading hours the strategy spent holding it. */
        double exposureOf(long id, List<Trade> trades, Instant from, Instant to) {
            NavigableMap<Long, Double> hours = byInstrument.get(id);

            if (hours == null || hours.isEmpty()) {
                return 0;
            }

            long open = hours.subMap(bucketOf(from), true, bucketOf(to), false).size();
            long held = 0;

            for (Trade trade : trades) {
                held += hours.subMap(bucketOf(trade.entry()), true, bucketOf(trade.exit()), false).size();
            }

            return open == 0 ? 0 : (double) held / open;
        }

        /**
         * What the average instrument did between these two moments, in per cent. An instrument that did
         * not trade in one of the hours is left out of the average rather than carried at its last price:
         * a name that was not trading is not part of the market of that hour.
         * <p>
         * The start is read forwards and the end backwards, so that a span reaching past either edge of
         * what was loaded still has two prices to compare - at the edges there is nothing earlier to fall
         * back to, and an hour with no bar of its own would otherwise leave the whole span unanswered.
         */
        double moveBetween(Instant from, Instant to) {
            double total = 0;
            int counted = 0;

            for (NavigableMap<Long, Double> hours : byInstrument.values()) {
                Map.Entry<Long, Double> before = firstAtOrAround(hours, bucketOf(from));
                Map.Entry<Long, Double> after = hours.floorEntry(bucketOf(to));

                if (before == null || after == null || before.getValue() <= 0 || before.getKey().equals(after.getKey())) {
                    continue;
                }

                total += 100 * (after.getValue() / before.getValue() - 1);
                counted++;
            }

            return counted == 0 ? Double.NaN : total / counted;
        }

        /** The price of that hour, or of the nearest one before it, or - at the very start - after it. */
        private static Map.Entry<Long, Double> firstAtOrAround(NavigableMap<Long, Double> hours, long bucket) {
            Map.Entry<Long, Double> before = hours.floorEntry(bucket);

            return before != null ? before : hours.ceilingEntry(bucket);
        }

        private static long bucketOf(Instant time) {
            return Math.floorDiv(time.toEpochMilli(), CandleInterval.HOUR.getDuration().toMillis());
        }
    }

    /**
     * Each trade's result in per cent of what it spent: every executed payment - buys, fees, the sale -
     * summed until a sale closes the trade. The sale's own fee is journalled beside it at the same
     * moment, in either order, so a trade closes only once the moment of its sale has passed.
     */
    static List<Trade> tradesOf(List<Operation> operations) {
        List<Trade> trades = new ArrayList<>();
        double spent = 0;
        double net = 0;
        Instant boughtAt = null;
        Instant soldAt = null;

        for (Operation operation : operations.stream()
            .filter(operation -> operation.state() == OperationState.EXECUTED)
            .filter(operation -> operation.executedDate() != null)
            .sorted(Comparator.comparing(Operation::executedDate))
            .toList()) {
            if (soldAt != null && !operation.executedDate().equals(soldAt)) {
                trades.add(new Trade(boughtAt, soldAt, 100 * net / spent));
                spent = 0;
                net = 0;
                boughtAt = null;
                soldAt = null;
            }

            double payment = operation.payment() == null ? 0 : operation.payment().toDouble();

            if (operation.direction() == OperationType.BUY) {
                boughtAt = boughtAt == null ? operation.executedDate() : boughtAt;
                spent += Math.abs(payment);
            }

            // Nothing has been bought yet, so this belongs to no trade: a sale of what an earlier trade
            // left behind, or its fee. Counting it here would hand the next trade someone else's money
            // and divide it by a spend it never made.
            if (spent == 0) {
                continue;
            }

            net += payment;

            if (operation.direction() == OperationType.SELL && spent > 0) {
                soldAt = operation.executedDate();
            }
        }

        if (soldAt != null) {
            trades.add(new Trade(boughtAt, soldAt, 100 * net / spent));
        }

        return trades;
    }
}
