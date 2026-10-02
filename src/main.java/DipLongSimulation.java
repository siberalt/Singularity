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
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.impl.quantity.TradeCapacity;
import com.siberalt.singularity.strategy.impl.quantity.TradeMoment;
import com.siberalt.singularity.strategy.impl.quantity.TradeQuantity;
import com.siberalt.singularity.strategy.simulation.runner.StrategyBacktester;
import com.siberalt.singularity.strategy.simulation.runner.StrategyResult;
import com.siberalt.singularity.strategy.upside.FilterUpsideCalculator;
import com.siberalt.singularity.strategy.upside.FixedSignalReverserUpsideCalculator;
import com.siberalt.singularity.strategy.upside.InvertedUpsideCalculator;
import com.siberalt.singularity.strategy.upside.PriceChangeUpsideCalculator;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.WindowUpsideCalculator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The dip rule run through the event simulator, one instrument at a time.
 * <p>
 * What the stand measured, and what this runs: a fall of four per cent or more from the highest price of
 * the last thirty candles - read by {@link PriceChangeUpsideCalculator}, from the window's high, counted
 * only while the part after that high holds nothing below the end - bought at the market and held sixty
 * bars. The depth grid found the return proportional to the fall and crossing the cost of a round trip
 * between three and four per cent; four is the shallowest depth with events in the hundreds.
 * <p>
 * <b>The size growing with the depth of the low</b> is here, under {@code size}. The stand's weight is
 * {@code 1 + how many per cent the close sits under the median of the pivot lows of the last 2400 printed
 * minutes} - half a week - and a weight above one means nothing to an account that commits everything to
 * every trade. So it is read as a share of the account instead: {@code size=depth} puts a base trade on a
 * third of the balance and the deepest on all of it, capped at three times the base. That makes the
 * account curve comparable only against the same capital deployed without the weighting, which is what
 * {@code size=flat} runs - a third of the balance on every trade. {@code size=full} is the whole balance,
 * as before, and all three are reported.
 * <p>
 * One part of the measured rule is not here: <b>the stop at ten per cent</b>, because the mock broker has
 * no stop orders. On the stand it fired on one trade in a hundred and cost three tenths of a basis point,
 * so its absence flatters the result by that much and leaves the worst trade unbounded.
 * The hygiene the stand applied by hand is available to the strategy through
 * {@link FilterUpsideCalculator}: with {@code session=1} a window of thirty candles that took more than
 * forty five minutes of wall clock is refused, because a window that long has a break in it. Both are run.
 * <p>
 * Costs are the broker's own and by side: commission 0.05% each way, half a spread 0.015%, slippage 0.06%.
 * That is 25 basis points a round trip against the 35 the stand charges, so this should read a little
 * better than the stand for that reason alone - and a little worse for the entry landing a bar later than
 * the stand's next open.
 */
public class DipLongSimulation {
    private static final long[] INSTRUMENTS = {4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
    private static final Instant FROM = Instant.parse("2023-01-03T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");
    private static final double COMMISSION = 0.0005;
    private static final Money INITIAL = Money.of("RUB", 1_000_000.00);

    /** One completed trade and what it made against the market of the same minutes. */
    record Trade(long instrument, Instant entry, Instant exit, double percent, double excess,
                 boolean overBreak, boolean exDay) {
    }

    public static void main(String[] args) throws Exception {
        ConfigInterface configuration = new YamlConfig(Files.newInputStream(Paths.get("src/main/resources/app.yaml")));
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candles = new SqliteCandleRepositoryFactory().create(dbPath);
        SqliteInstrumentRepository instruments = new SqliteInstrumentRepository(DriverManager.getConnection(dbPath));
        // Named arguments: fall=4, span=30, hold=60, session=1, fee=0.0005, ids=4,5,7, from=..., to=...
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
        double fall = Double.parseDouble(options.getOrDefault("fall", "4"));
        int span = Integer.parseInt(options.getOrDefault("span", "30"));
        int hold = Integer.parseInt(options.getOrDefault("hold", "60"));
        double commission = Double.parseDouble(options.getOrDefault("fee", String.valueOf(COMMISSION)));
        boolean inSession = !options.getOrDefault("session", "0").equals("0");
        String size = options.getOrDefault("size", "full");
        int lowWindow = Integer.parseInt(options.getOrDefault("low", "2400"));
        double cap = Double.parseDouble(options.getOrDefault("cap", "3"));

        System.out.printf(Locale.ROOT,
            "%s .. %s: купить падение на %.1f%% от максимума %d свечей, держать %d, комиссия %.3f%% "
                + "за сторону%n", from, to, fall, span, hold, 100 * commission);

        if (inSession) {
            System.out.println("  окна, перепрыгнувшие перерыв в торгах, отвергаются");
        }

        System.out.println(switch (size) {
            case "depth" -> String.format(Locale.ROOT, "  размер позиции: от 1/%.0f счёта до всего счёта, "
                + "пропорционально тому, насколько ниже медианы минимумов за %d баров", cap, lowWindow);
            case "flat" -> String.format(Locale.ROOT, "  размер позиции: 1/%.0f счёта на каждую сделку", cap);
            default -> "  размер позиции: весь счёт на каждую сделку";
        });

        RsiLimitEntrySimulation.Market market = RsiLimitEntrySimulation.Market.of(candles, INSTRUMENTS,
            from, to);

        System.out.printf("%4s %10s %8s %7s %9s %11s %9s%n",
            "id", "profit %", "сделок", "плюс", "среднее %", "избыток %", "в рынке");

        List<Double> profits = new ArrayList<>();
        List<Trade> all = new ArrayList<>();

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
            broker.getOrderService().getLiquidityModel().setInfiniteLiquidity(true);

            StrategyResult result = new StrategyBacktester<EventMockBroker>(
                (range, accountId, simulated, observer) -> {
                    // The calculator says "it has fallen" with -1; inverted that is +1, a buy, so the
                    // reverser is told about rises: it reads the sign of the signal, not the direction of
                    // the price. Its own -1, hold bars later, sells the position out.
                    UpsideCalculator falls = new PriceChangeUpsideCalculator(span, 100, fall);

                    if (inSession) {
                        falls = new FilterUpsideCalculator(falls, window -> window.size() > span
                            && Duration.between(window.get(window.size() - 1 - span).getTime(),
                            window.getLast().getTime()).toMinutes() <= 3L * span / 2);
                    }

                    UpsideCalculator signals = new WindowUpsideCalculator(
                        FixedSignalReverserUpsideCalculator.ofRises(
                            new InvertedUpsideCalculator(falls), hold, 1),
                        2 * span
                    );
                    // Outside the window calculator, which hands its delegate only 2 * span bars: the
                    // weight needs half a week of them.
                    UpsideCalculator sized = size.equals("depth")
                        ? new DepthSizedUpsideCalculator(signals, lowWindow, cap)
                        : signals;

                    new BasicTradeStrategy(simulated, uid, accountId, sized, candles)
                        .setLookbackCandles(Math.max(60 * 24, lowWindow + 2L * span))
                        .setBuyThreshold(0.9)
                        .setSellThreshold(-0.9)
                        .setStep(1)
                        .setTradeQuantity(shareOf(size, cap))
                        .run(observer);
                },
                broker,
                uid,
                INITIAL,
                clock
            ).run(from, to);

            List<Trade> trades = tradesOf(RsiLimitEntrySimulation.tradesOf(
                operations.getByAccountId(result.accountId(), new TimeRange(from, to))), market, id);

            profits.add(result.profitPercent());
            all.addAll(trades);

            System.out.printf(Locale.ROOT, "%4d %+10.2f %8d %6.0f%% %+9.3f %+11.3f %8.1f%%%n", id,
                result.profitPercent(), trades.size(),
                100.0 * trades.stream().filter(trade -> trade.percent() > 0).count() / Math.max(1, trades.size()),
                trades.stream().mapToDouble(Trade::percent).average().orElse(0),
                trades.stream().filter(trade -> !Double.isNaN(trade.excess())).mapToDouble(Trade::excess)
                    .average().orElse(0),
                100 * PriceChangeShortSimulation.exposureOf(market, id, toShorts(trades), from, to));
        }

        System.out.printf(Locale.ROOT, "%nсреднее по бумагам %+.2f%%, положительных %d из %d%n",
            profits.stream().mapToDouble(Double::doubleValue).average().orElse(0),
            profits.stream().filter(profit -> profit > 0).count(), profits.size());

        row("все сделки", all);
        row("окно внутри сессии", all.stream().filter(trade -> !trade.overBreak()).toList());
        row("и в обычный день", all.stream()
            .filter(trade -> !trade.overBreak() && !trade.exDay())
            .toList());
        row("окно через перерыв", all.stream().filter(Trade::overBreak).toList());
        row("в день отсечки", all.stream().filter(Trade::exDay).toList());

        if (!DepthSizedUpsideCalculator.SHARES.isEmpty()) {
            double[] sorted = DepthSizedUpsideCalculator.SHARES.stream()
                .mapToDouble(Double::doubleValue)
                .sorted()
                .toArray();

            System.out.printf(Locale.ROOT,
                "%nдоля счёта на сделку: медиана %.0f%%, среднее %.0f%%, на пределе %.0f%% сделок%n",
                100 * sorted[sorted.length / 2],
                100 * Arrays.stream(sorted).average().orElse(0),
                100.0 * Arrays.stream(sorted).filter(share -> share >= 0.999).count() / sorted.length);
        }
    }

    /**
     * How much of the account one buy commits. A sell always closes the whole position, whatever opened it.
     * <p>
     * {@code depth} reads the share off the signal's second channel, where
     * {@link DepthSizedUpsideCalculator} wrote it; {@code flat} spends the same share on every trade, which
     * is the control the weighted run has to beat; {@code full} is the default sizing - everything, every
     * time.
     */
    static TradeQuantity shareOf(String size, double cap) {
        return new TradeQuantity() {
            @Override
            public long toBuy(TradeMoment moment, TradeCapacity capacity) {
                double share = switch (size) {
                    case "depth" -> moment.upside().strength();
                    case "flat" -> 1 / cap;
                    default -> 1;
                };

                return (long) (capacity.totalLots() * Math.min(1, Math.max(0, share)));
            }

            @Override
            public long toSell(TradeMoment moment, TradeCapacity capacity) {
                return capacity.positionLots();
            }
        };
    }

    /**
     * The rule's signal with the share of the account written into its second channel: the deeper the close
     * sits under the median of the window's pivot lows, the larger the position.
     * <p>
     * The weight the stand measured is {@code 1 + per cent below that median}, which is a number above one
     * and means nothing to an account with no leverage. Divided by its cap it becomes a share: at the cap
     * the trade is the whole balance, without any depth it is one cap-th of it. Capping is not a detail -
     * the per cent below the median has no upper bound, and one unusual signal would otherwise ask for a
     * position the account cannot take.
     * <p>
     * Only a buy is touched. The closing signal keeps its own strength, because what closes a position is
     * the position, not a share of the balance.
     * <p>
     * It keeps the long window itself and hands the delegate only the candles it was given. That is not a
     * detail either: {@link BasicTradeStrategy} empties its list after every bar, so a calculator is given
     * one candle at a time and whatever window it needs is a window it accumulated - which is what
     * {@link WindowUpsideCalculator} is for. Wrapping that one in another of half a week would re-add two
     * and a half thousand candles on every bar of every instrument, so the two windows are kept side by
     * side instead of nested.
     */
    static class DepthSizedUpsideCalculator implements UpsideCalculator {
        /**
         * The share each buy asked for, over every instrument. Without it the weighted run cannot be
         * compared with the one that commits everything: the whole point is earning the same on less.
         */
        static final List<Double> SHARES = Collections.synchronizedList(new ArrayList<>());

        private static final int PIVOT = 2;

        private final UpsideCalculator delegate;
        private final int window;
        private final double cap;
        private final Deque<Candle> seen = new ArrayDeque<>();

        DepthSizedUpsideCalculator(UpsideCalculator delegate, int window, double cap) {
            this.delegate = delegate;
            this.window = window;
            this.cap = cap;
        }

        @Override
        public Upside calculate(List<Candle> lastCandles) {
            seen.addAll(lastCandles);

            while (seen.size() > window + 1) {
                seen.pollFirst();
            }

            Upside upside = delegate.calculate(lastCandles);

            if (upside.signal() <= 0) {
                return upside;
            }

            double share = Math.min(1, (1 + belowMedian(seen.stream().toList())) / cap);

            SHARES.add(share);

            return new Upside(upside.signal(), share);
        }

        /** How many per cent the last close sits under the median of the window's pivot lows, or zero. */
        private double belowMedian(List<Candle> lastCandles) {
            int last = lastCandles.size() - 1;

            if (last < window) {
                return 0;
            }

            double now = lastCandles.get(last).getCloseAsDouble();
            List<Double> lows = new ArrayList<>();

            for (int at = last - PIVOT; at >= last - window + PIVOT; at--) {
                boolean low = true;

                for (int step = 1; step <= PIVOT; step++) {
                    low &= lastCandles.get(at).getLowAsDouble() < lastCandles.get(at - step).getLowAsDouble()
                        && lastCandles.get(at).getLowAsDouble() < lastCandles.get(at + step).getLowAsDouble();
                }

                if (low) {
                    lows.add(lastCandles.get(at).getLowAsDouble());
                }
            }

            if (lows.size() < 4 || now <= 0) {
                return 0;
            }

            double[] sorted = lows.stream().mapToDouble(Double::doubleValue).sorted().toArray();

            return Math.max(0, 100 * (sorted[sorted.length / 2] / now - 1));
        }
    }

    /** The mean excess and the error across instruments, which is the error the stand reports. */
    static void row(String label, List<Trade> trades) {
        if (trades.isEmpty()) {
            return;
        }

        Map<Long, List<Double>> byInstrument = new HashMap<>();

        for (Trade trade : trades) {
            if (!Double.isNaN(trade.excess())) {
                byInstrument.computeIfAbsent(trade.instrument(), id -> new ArrayList<>())
                    .add(trade.excess());
            }
        }

        List<Double> means = byInstrument.values().stream()
            .map(values -> values.stream().mapToDouble(Double::doubleValue).average().orElse(0))
            .toList();
        double mean = means.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = means.stream().mapToDouble(one -> (one - mean) * (one - mean)).sum()
            / Math.max(1, means.size() - 1);

        System.out.printf(Locale.ROOT, "%-24s %5d сделок, %+.3f%% на сделку, избыток %+.3f%% ± %.3f%n",
            label, trades.size(), trades.stream().mapToDouble(Trade::percent).average().orElse(0), mean,
            Math.sqrt(variance / Math.max(1, means.size())));
    }

    /** The account's own trades, read against the market of the same minutes. */
    static List<Trade> tradesOf(List<RsiLimitEntrySimulation.Trade> trades,
                                RsiLimitEntrySimulation.Market market, long instrument) {
        List<Trade> read = new ArrayList<>();

        for (RsiLimitEntrySimulation.Trade trade : trades) {
            double move = market.moveBetween(trade.entry(), trade.exit());

            read.add(new Trade(instrument, trade.entry(), trade.exit(), trade.percent(),
                Double.isNaN(move) ? Double.NaN : trade.percent() - move,
                PriceChangeShortSimulation.overBreak(market, instrument, trade.entry()),
                PriceChangeShortSimulation.exDay(market, instrument, trade.entry())));
        }

        return read;
    }

    /** The exposure helper counts spans, and takes the short's record; the two carry the same instants. */
    static List<PriceChangeShortSimulation.Short> toShorts(List<Trade> trades) {
        return trades.stream()
            .map(trade -> new PriceChangeShortSimulation.Short(trade.instrument(), trade.entry(),
                trade.exit(), trade.percent(), trade.excess(), trade.overBreak(), trade.exDay()))
            .toList();
    }
}
