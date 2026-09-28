import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
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
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.impl.quantity.TradeCapacity;
import com.siberalt.singularity.strategy.impl.quantity.TradeMoment;
import com.siberalt.singularity.strategy.impl.quantity.TradeQuantity;
import com.siberalt.singularity.strategy.simulation.runner.StrategyBacktester;
import com.siberalt.singularity.strategy.simulation.runner.StrategyResult;
import com.siberalt.singularity.strategy.upside.FixedSignalReverserUpsideCalculator;
import com.siberalt.singularity.strategy.upside.InvertedUpsideCalculator;
import com.siberalt.singularity.strategy.upside.PriceChangeUpsideCalculator;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.WindowUpsideCalculator;

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
 * The short of a sharp rise run through the event simulator, one instrument at a time.
 * <p>
 * The measurement behind it was of returns per signal on a stand of its own; this is the same rule as an
 * account would live it - a sale of lots the account does not own, money committed while the short is on,
 * a commission on each side, and both legs crossing the spread. The mock broker is told
 * {@link com.siberalt.singularity.broker.impl.mock.MockOrderService#setShortsAllowed shorts are allowed},
 * which models the position going below zero and nothing else about a short: no margin requirement, no
 * borrow fee, and no question of whether the lots could be borrowed at all. For an hour-long short the
 * borrow is a fraction of a basis point, so the omission is small - but on a thin name the availability is
 * not a detail, and no simulation here can speak to it.
 * <p>
 * The signal is {@link PriceChangeUpsideCalculator} as it now reads a window: the rise measured from the
 * lowest price in it, and counted only while the window still ends on its own highest. Its threshold is
 * therefore not the stand's seven per cent - a rise of seven from the window's low is a move of about five
 * and a half between its ends - so ten is the number that trades what the stand measured at seven.
 * <p>
 * Two things the stand did that a strategy cannot: it threw out windows that spanned a break in trading
 * and days the instrument went ex-dividend. The strategy trades everything it sees, which is the honest
 * thing for it to do, so the trades are split here after the fact by the same two tests - the sixty
 * minutes before the entry holding a gap of more than half an hour, and the entry day opening two per cent
 * below the market. That makes the simulation's rows comparable with the stand's without pretending the
 * strategy knew.
 */
public class PriceChangeShortSimulation {
    private static final long[] INSTRUMENTS = {4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
    private static final Instant FROM = Instant.parse("2023-01-03T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");
    /** Per side, as T-Bank charges on the investor tariff. */
    private static final double COMMISSION = 0.0005;
    private static final Money INITIAL = Money.of("RUB", 1_000_000.00);
    private static final long MINUTE = 60_000L;
    private static final long BREAK = 30 * MINUTE;
    private static final long DAY = 24 * 3600_000L;
    /** An overnight move the market did not share, at which a dividend is the likely reason. */
    private static final double EX_GAP = -2;

    public static void main(String[] args) throws Exception {
        ConfigInterface configuration = new YamlConfig(Files.newInputStream(Paths.get("src/main/resources/app.yaml")));
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candles = new SqliteCandleRepositoryFactory().create(dbPath);
        SqliteInstrumentRepository instruments = new SqliteInstrumentRepository(DriverManager.getConnection(dbPath));
        // Named arguments: rise=10, span=60, hold=60, fee=0.0005, ids=4,5,7, from=..., to=...
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
        double rise = Double.parseDouble(options.getOrDefault("rise", "10"));
        int span = Integer.parseInt(options.getOrDefault("span", "60"));
        int hold = Integer.parseInt(options.getOrDefault("hold", "60"));
        double commission = Double.parseDouble(options.getOrDefault("fee", String.valueOf(COMMISSION)));

        System.out.printf(Locale.ROOT,
            "%s .. %s: short a rise of %.1f%% measured from the low of %d candles, held %d, "
                + "commission %.3f%% a side%n", from, to, rise, span, hold, 100 * commission);

        RsiLimitEntrySimulation.Market market = RsiLimitEntrySimulation.Market.of(candles, INSTRUMENTS,
            from, to);

        System.out.printf("%4s %10s %8s %7s %9s %11s %9s%n",
            "id", "profit %", "shorts", "wins", "mean %", "excess %", "in market");

        List<Double> profits = new ArrayList<>();
        List<Short> all = new ArrayList<>();

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
            EventMockBroker broker = EventMockBroker.builder(candles, instruments, listing,
                    new InMemoryOrderRepository(), operations, clock)
                .setCommissionRatio(commission)
                .build();

            broker.getOrderService().getPriceModel().setHalfSpreadRatio(0.00015).setSlippageImpactRatio(0.0006);
            // How much the idea earns, not how much money it can carry.
            broker.getOrderService().getLiquidityModel().setInfiniteLiquidity(true);
            broker.getOrderService().setShortsAllowed(true);

            StrategyResult result = new StrategyBacktester<EventMockBroker>(
                (range, accountId, simulated, observer) -> {
                    // The calculator says "it has risen" with +1; inverted that is -1, a sell, which is
                    // the side the reverser has to be told about - it reads the sign of the signal, not
                    // the direction of the price. Its own +1, hold bars later, buys the short back.
                    UpsideCalculator sell = new InvertedUpsideCalculator(
                        new PriceChangeUpsideCalculator(span, rise, 100)
                    );

                    new BasicTradeStrategy(simulated, uid, accountId,
                        new WindowUpsideCalculator(
                            FixedSignalReverserUpsideCalculator.ofFalls(sell, hold, 1),
                            2 * span
                        ),
                        candles)
                        .setTradeQuantity(shorts())
                        .setLookbackCandles(60 * 24)
                        .setBuyThreshold(0.9)
                        .setSellThreshold(-0.9)
                        .setStep(1)
                        .run(observer);
                },
                broker,
                uid,
                INITIAL,
                clock
            ).run(from, to);

            List<Short> shorts = shortsOf(operations.getByAccountId(result.accountId(),
                new TimeRange(from, to)), market, id);

            profits.add(result.profitPercent());
            all.addAll(shorts);

            System.out.printf(Locale.ROOT, "%4d %+10.2f %8d %6.0f%% %+9.3f %+11.3f %8.1f%%%n", id,
                result.profitPercent(), shorts.size(),
                100.0 * shorts.stream().filter(one -> one.percent() > 0).count() / Math.max(1, shorts.size()),
                shorts.stream().mapToDouble(Short::percent).average().orElse(0),
                shorts.stream().filter(one -> !Double.isNaN(one.excess())).mapToDouble(Short::excess)
                    .average().orElse(0),
                100 * exposureOf(market, id, shorts, from, to));
        }

        System.out.printf(Locale.ROOT, "%nmean profit per instrument %+.2f%%, positive on %d of %d%n",
            profits.stream().mapToDouble(Double::doubleValue).average().orElse(0),
            profits.stream().filter(profit -> profit > 0).count(), profits.size());

        row("every short", all);
        row("window inside a session", all.stream().filter(one -> !one.overBreak()).toList());
        row("and on an ordinary day", all.stream()
            .filter(one -> !one.overBreak() && !one.exDay())
            .toList());
        row("window over a break", all.stream().filter(Short::overBreak).toList());
        row("on an ex-dividend day", all.stream().filter(Short::exDay).toList());
    }

    /**
     * The mean excess and the error across instruments, which is the error the stand reports: trades of
     * one name are not independent of each other.
     */
    static void row(String label, List<Short> shorts) {
        if (shorts.isEmpty()) {
            return;
        }

        Map<Long, List<Double>> byInstrument = new HashMap<>();

        for (Short one : shorts) {
            if (!Double.isNaN(one.excess())) {
                byInstrument.computeIfAbsent(one.instrument(), id -> new ArrayList<>()).add(one.excess());
            }
        }

        List<Double> means = byInstrument.values().stream()
            .map(values -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0))
            .toList();
        double mean = means.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = means.stream().mapToDouble(one -> (one - mean) * (one - mean)).sum()
            / Math.max(1, means.size() - 1);
        double error = Math.sqrt(variance / Math.max(1, means.size()));

        System.out.printf(Locale.ROOT, "%-26s %5d shorts, %+.3f%% a short, excess %+.3f%% ± %.3f%n", label,
            shorts.size(), shorts.stream().mapToDouble(Short::percent).average().orElse(0), mean, error);
    }

    /**
     * One completed short: sold, bought back, and what it made of the notional it sold.
     *
     * @param excess     the short's result less what the market did over the same minutes, which for a
     *                   short means the market's move is added rather than subtracted
     * @param overBreak  whether the hour before the entry held a break in trading, which the stand threw
     *                   out as an overnight gap dressed up as a move
     * @param exDay      whether the entry day opened far below the market, a likely dividend
     */
    record Short(long instrument, Instant entry, Instant exit, double percent, double excess,
                 boolean overBreak, boolean exDay) {
    }

    /**
     * Each short's result in per cent of the notional it sold: the sale and its fee, then everything up to
     * and including the buy that closes it. A sale with nothing sold before it opens a short; the buy that
     * brings the position back to zero ends it.
     */
    static List<Short> shortsOf(List<Operation> operations, RsiLimitEntrySimulation.Market market,
                                long instrument) {
        List<Short> shorts = new ArrayList<>();
        double sold = 0;
        double net = 0;
        Instant soldAt = null;
        Instant boughtAt = null;

        for (Operation operation : operations.stream()
            .filter(operation -> operation.state() == OperationState.EXECUTED)
            .filter(operation -> operation.executedDate() != null)
            .sorted(Comparator.comparing(Operation::executedDate))
            .toList()) {
            if (boughtAt != null && !operation.executedDate().equals(boughtAt)) {
                shorts.add(shortOf(instrument, soldAt, boughtAt, 100 * net / sold, market));
                sold = 0;
                net = 0;
                soldAt = null;
                boughtAt = null;
            }

            double payment = operation.payment() == null ? 0 : operation.payment().toDouble();

            if (operation.direction() == OperationType.SELL) {
                soldAt = soldAt == null ? operation.executedDate() : soldAt;
                sold += Math.abs(payment);
            }

            // Nothing has been sold yet, so this belongs to no short - a fee of a trade already closed.
            if (sold == 0) {
                continue;
            }

            net += payment;

            if (operation.direction() == OperationType.BUY) {
                boughtAt = operation.executedDate();
            }
        }

        if (boughtAt != null && sold > 0) {
            shorts.add(shortOf(instrument, soldAt, boughtAt, 100 * net / sold, market));
        }

        return shorts;
    }

    static Short shortOf(long instrument, Instant entry, Instant exit, double percent,
                         RsiLimitEntrySimulation.Market market) {
        double move = market.moveBetween(entry, exit);
        // A short earns the opposite of the move, so the market's leg is added: what is left is the part
        // of the fall that was this instrument's own.
        double excess = Double.isNaN(move) ? Double.NaN : percent + move;

        return new Short(instrument, entry, exit, percent, excess, overBreak(market, instrument, entry),
            exDay(market, instrument, entry));
    }

    /** Whether the hour before the entry holds a gap longer than a break, i.e. spans a night. */
    static boolean overBreak(RsiLimitEntrySimulation.Market market, long instrument, Instant entry) {
        RsiLimitEntrySimulation.Prices prices = market.byInstrument().get(instrument);

        if (prices == null) {
            return false;
        }

        long[] minutes = prices.minutes();
        int at = Arrays.binarySearch(minutes, entry.toEpochMilli());
        int to = at >= 0 ? at : -at - 2;

        for (int back = to; back > 0 && minutes[to] - minutes[back] < 60 * MINUTE; back--) {
            if (minutes[back] - minutes[back - 1] > BREAK) {
                return true;
            }
        }

        return false;
    }

    /** Whether this instrument opened two per cent below the market on the day of the entry. */
    static boolean exDay(RsiLimitEntrySimulation.Market market, long instrument, Instant entry) {
        RsiLimitEntrySimulation.Prices prices = market.byInstrument().get(instrument);

        if (prices == null) {
            return false;
        }

        long[] minutes = prices.minutes();
        double[] closes = prices.closes();
        long day = Math.floorDiv(entry.toEpochMilli(), DAY);
        int at = Arrays.binarySearch(minutes, entry.toEpochMilli());
        int from = at >= 0 ? at : -at - 2;

        while (from > 0 && Math.floorDiv(minutes[from - 1], DAY) == day) {
            from--;
        }

        if (from == 0) {
            return false;
        }

        double own = 100 * (closes[from] / closes[from - 1] - 1);
        double move = market.moveBetween(Instant.ofEpochMilli(minutes[from - 1]),
            Instant.ofEpochMilli(minutes[from]));

        return !Double.isNaN(move) && own - move <= EX_GAP;
    }

    static double exposureOf(RsiLimitEntrySimulation.Market market, long id, List<Short> shorts,
                             Instant from, Instant to) {
        RsiLimitEntrySimulation.Prices prices = market.byInstrument().get(id);

        if (prices == null) {
            return 0;
        }

        long open = prices.tradedHours(from, to);
        long held = 0;

        for (Short one : shorts) {
            held += prices.tradedHours(one.entry(), one.exit());
        }

        return open == 0 ? 0 : (double) held / open;
    }

    /**
     * Sells what the account could otherwise have bought, and buys back exactly the short it is holding.
     * <p>
     * A short cannot be sized by what is held, which is what every sizing here does - the position starts
     * at nothing and ends below zero. The notional is what the cash would have bought instead, so that the
     * per-cent figures mean the same as the long runs', and the close is the position itself rather than a
     * share of it: a short left half open has no exit of its own.
     */
    static TradeQuantity shorts() {
        return new TradeQuantity() {
            @Override
            public long toBuy(TradeMoment moment, TradeCapacity capacity) {
                return capacity.positionLots() < 0 ? -capacity.positionLots() : 0;
            }

            @Override
            public long toSell(TradeMoment moment, TradeCapacity capacity) {
                return capacity.positionLots() == 0 ? capacity.affordableLots() : 0;
            }
        };
    }
}
