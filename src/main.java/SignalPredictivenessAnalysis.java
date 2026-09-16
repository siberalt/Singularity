import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.analysis.PredictivenessReport;
import com.siberalt.singularity.strategy.analysis.SignalPredictiveness;
import com.siberalt.singularity.strategy.analysis.VarianceRatio;
import com.siberalt.singularity.strategy.upside.RangeSwitchUpsideCalculator;
import com.siberalt.singularity.strategy.upside.SmoothSwitchUpsideCalculator;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.extreme.cache.CachingExtremeLocator;
import com.siberalt.singularity.strategy.level.linear.StatelessClusterLevelDetector;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.level.selector.StrongestLevelPairSelector;
import com.siberalt.singularity.strategy.upside.InvertedUpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.KeyLevelsUpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.SimpleLevelBasedUpsideCalculator;
import com.siberalt.singularity.strategy.upside.MeanReversionUpsideCalculator;
import com.siberalt.singularity.strategy.upside.SlopeUpsideCalculator;
import com.siberalt.singularity.strategy.upside.VolumeImbalanceUpsideCalculator;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Measures what a signal knows about the future, on real candles, before any strategy is built on
 * it. Run this on a new {@link UpsideCalculator} before running a simulation with it: a simulation
 * that comes back negative cannot tell you whether the signal was wrong or the sizing was, and this
 * can.
 * <p>
 * Read the output in this order. If the executable correlation is inside the noise floor there is
 * nothing there, whatever the rest says. If it is outside, the edge in basis points is what the
 * signal is worth per trade, and it has to beat a round trip - commission twice plus the spread
 * twice - before any of it reaches the account. The same-bar column is a control: it is what the
 * signal appears to be worth if a backtest lets an order fill inside the bar it was read from, and
 * the distance between the two columns is the size of that mistake.
 * <p>
 * Arguments: instrument uid, from, to, and one or more intervals. Everything has a default.
 * <p>
 * { -Dsignal=flow} measures the order-flow imbalance instead of the price slope. The two are
 * worth comparing directly: everything price-derived is a rearrangement of the same closes, and
 * only the flow is a separate observation.
 */
public class SignalPredictivenessAnalysis {
    // TMOS, by our id. An instrument may be named by its id or by the uid a broker lists it under.
    private static final String DEFAULT_INSTRUMENT = "2";
    private static final String DEFAULT_FROM = "2021-01-01T00:00:00Z";
    private static final String DEFAULT_TO = "2024-01-01T00:00:00Z";

    /**
     * How far back the calculator gets to look, per interval. A day of minutes is what the strategy
     * uses; the wider intervals get fewer bars but a longer span of history, which is what the
     * normalising most calculators do needs to be steady.
     */
    private static final Map<CandleInterval, Integer> LOOKBACK = Map.of(
        CandleInterval.MIN_1, 60 * 24,
        CandleInterval.HOUR, 500,
        CandleInterval.DAY, 250
    );

    /** Our id for the instrument, given either as that id or as the uid a broker lists it under. */
    private static long instrumentIdOf(String instrument, String dbPath) {
        if (instrument.chars().allMatch(Character::isDigit)) {
            return Long.parseLong(instrument);
        }

        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(dbPath)) {
            return new com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository(connection)
                .idOf(instrument)
                .orElseThrow(() -> new IllegalArgumentException("No instrument is listed as " + instrument));
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        String instrument = args.length > 0 ? args[0] : DEFAULT_INSTRUMENT;
        Instant from = Instant.parse(args.length > 1 ? args[1] : DEFAULT_FROM);
        Instant to = Instant.parse(args.length > 2 ? args[2] : DEFAULT_TO);
        List<String> intervals = args.length > 3
            ? List.of(args).subList(3, args.length)
            : List.of("MIN_1", "HOUR", "DAY");

        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candleRepository = new SqliteCandleRepositoryFactory().create(dbPath);
        long instrumentId = instrumentIdOf(instrument, dbPath);

        List<Candle> minuteCandles = candleRepository.findAfterOrEqual(instrumentId, from, Integer.MAX_VALUE)
            .stream()
            .filter(candle -> candle.getTime().isBefore(to))
            .toList();

        System.out.printf("%s: %d minute bars, %s .. %s%n",
            instrument, minuteCandles.size(), from, to);

        if (minuteCandles.isEmpty()) {
            return;
        }

        CandleAggregator aggregator = new CandleAggregator();

        for (String name : intervals) {
            CandleInterval interval = CandleInterval.valueOf(name);
            List<Candle> candles = interval == CandleInterval.MIN_1
                ? minuteCandles
                : aggregator.aggregate(minuteCandles, interval);

            report(interval, candles);
        }
    }

    private static void report(CandleInterval interval, List<Candle> candles) {
        int lookback = LOOKBACK.getOrDefault(interval, 250);
        boolean[] breaks = breaksIn(candles);
        long broken = 0;

        for (boolean isBreak : breaks) {
            broken += isBreak ? 1 : 0;
        }

        if (broken > 0) {
            System.out.printf("%n  %d break(s) where the price jumps more than %.0f%% in one bar; samples whose%n"
                + "  window or holding period spans one are left out%n", broken, 100 * maxGap());
        }

        // A minute series runs to hundreds of thousands of bars and every one of them costs the
        // calculator a pass over its whole window; a wider one is small enough to take whole.
        int stride = Integer.parseInt(System.getProperty("stride",
            interval == CandleInterval.MIN_1 ? "10" : "1"));

        System.out.printf("%n===== %s : %d bars, lookback %d, stride %d, entry %.2f =====%n",
            interval, candles.size(), lookback, stride, signalThreshold());

        if (candles.size() < lookback + 100) {
            System.out.println("  not enough bars to say anything");
            return;
        }

        System.out.printf("%-8s %6s %6s | %s%n", "period", "fired", "lag1",
            "horizon: corr(exec) / edge bp ± error on trades vs hold bp / n   (same-bar corr)");

        for (int period : calculatorPeriods()) {
            String family = System.getProperty("signal", "slope");
            UpsideCalculator calculator = switch (family) {
                case "flow" -> new VolumeImbalanceUpsideCalculator(period);
                case "reversion" -> new MeanReversionUpsideCalculator(period)
                    .setMinStraightness(Double.parseDouble(System.getProperty("straight", "0")));
                // Where the price sits between the levels the recent history clustered around:
                // near support reads as something to buy, near resistance as something to sell.
                case "levels" -> new KeyLevelsUpsideCalculator(
                    StatelessClusterLevelDetector.createDefault(1.4, cached(PivotPointExtremeLocator.ofMinimums(period))),
                    StatelessClusterLevelDetector.createDefault(1.4, cached(PivotPointExtremeLocator.ofMaximums(period))),
                    new SimpleLevelBasedUpsideCalculator(),
                    new StrongestLevelPairSelector(2),
                    window -> Upside.NEUTRAL
                );
                // Which way a trend read should be taken is a property of the market rather than of
                // the signal: above one the moves carry on, below it they come back. Measured, not
                // guessed - see VarianceRatio - and here it picks one branch outright.
                case "switch" -> new RangeSwitchUpsideCalculator(
                    varianceRatio(),
                    List.of(
                        RangeSwitchUpsideCalculator.Branch.below(1, inverted(new SlopeUpsideCalculator(period))),
                        RangeSwitchUpsideCalculator.Branch.from(1, new SlopeUpsideCalculator(period))
                    )
                );
                // The same question answered by degrees: near a ratio of one the two readings cancel
                // and the signal fades, instead of flipping between bars on a coefficient's jitter.
                // {-Dwidth=0} makes it the switch again.
                case "blend" -> new SmoothSwitchUpsideCalculator(
                    varianceRatio(),
                    List.of(
                        SmoothSwitchUpsideCalculator.weighted(
                            new SlopeUpsideCalculator(period),
                            SmoothSwitchUpsideCalculator.rising(1, blendWidth())
                        ),
                        SmoothSwitchUpsideCalculator.weighted(
                            inverted(new SlopeUpsideCalculator(period)),
                            SmoothSwitchUpsideCalculator.falling(1, blendWidth())
                        )
                    )
                );
                default -> new SlopeUpsideCalculator(period);
            };

            if (Boolean.getBoolean("invert")) {
                calculator = new InvertedUpsideCalculator(calculator);
            }
            long startedAt = System.currentTimeMillis();
            PredictivenessReport report = new SignalPredictiveness()
                .setLookbackCandles(lookback)
                .setStride(stride)
                .setSignalThreshold(signalThreshold())
                .setBreaks(breaks)
                .measure(candles, calculator);
            long elapsed = System.currentTimeMillis() - startedAt;

            StringBuilder line = new StringBuilder();

            for (PredictivenessReport.HorizonStat stat : report.horizons()) {
                line.append(String.format(
                    "  h=%-3d %+.3f%s/%+7.1f±%-5.0f%s on %-5d vs %+7.1f/%-6d (%+.3f)",
                    stat.horizon(),
                    stat.executableCorrelation(),
                    stat.isAboveNoise() ? "*" : " ",
                    stat.edgeBasisPoints(),
                    stat.edgeStandardErrorBasisPoints(),
                    stat.isEdgeAboveNoise() ? "*" : " ",
                    stat.edgeSamples(),
                    stat.baselineBasisPoints(),
                    stat.samples(),
                    stat.sameBarCorrelation()
                ));
            }

            System.out.printf(
                "%-4s%-4d %5.1f%% %+.3f |%s  [%ds]%n",
                (Boolean.getBoolean("invert") ? "-" : "") + switch (family) {
                    case "flow" -> "flow";
                    case "reversion" -> "rev";
                    case "levels" -> "lvl";
                    case "switch" -> "swt";
                    case "blend" -> "bln";
                    default -> "slp";
                },
                period,
                100.0 * report.firedBars() / Math.max(1, report.bars()),
                report.lag1Autocorrelation(),
                line,
                elapsed / 1000
            );
        }

        System.out.println("  * after a correlation = outside the noise floor, over samples that do not overlap");
        System.out.println("  * after an edge = more than twice its own standard error");
    }

    /**
     * The longest stretch with no break in it, a break being a jump no market made: a share that
     * closes at 1828 and opens at 945 has not fallen by half, it has split or paid a dividend the
     * feed did not adjust for. One such bar in sixteen thousand is enough to matter - it lands in a
     * sample as a return twenty times the usual size, and the selection that follows picks whichever
     * setting the artefact happened to miss. Prices are not patched, because the ratio of the event
     * is not known here; the series is simply cut and the longer side kept.
     */
    private static boolean[] breaksIn(List<Candle> candles) {
        boolean[] breaks = new boolean[candles.size()];

        for (int bar = 1; bar < candles.size(); bar++) {
            breaks[bar] = isBreak(candles.get(bar - 1), candles.get(bar));
        }

        return breaks;
    }

    private static boolean isBreak(Candle before, Candle after) {
        double close = before.getCloseAsDouble();
        double open = after.getOpenAsDouble();

        return close > 0 && open > 0 && Math.abs(open / close - 1) > maxGap();
    }

    private static double maxGap() {
        return Double.parseDouble(System.getProperty("gap", "0.2"));
    }

    /** What the market is doing, measured over the same window the calculators read. */
    private static MarketCoefficient varianceRatio() {
        int horizon = Integer.getInteger("vr", 10);

        return candles -> new VarianceRatio().measure(candles, horizon);
    }

    /**
     * How strong a reading has to be before it counts towards the edge - what a strategy would take
     * as its entry. A blended signal is scaled down by construction and rarely reaches the 0.9 a
     * switch reaches, so measuring the two at one threshold is the only way to compare them.
     */
    private static double signalThreshold() {
        return Double.parseDouble(System.getProperty("threshold", "0.9"));
    }

    /** How much of a coefficient either side of one the two readings share the answer over. */
    private static double blendWidth() {
        return Double.parseDouble(System.getProperty("width", "0.15"));
    }

    private static UpsideCalculator inverted(UpsideCalculator calculator) {
        return new InvertedUpsideCalculator(calculator);
    }

    /**
     * Puts a locator behind the range cache, which {@code -Dcache=true} turns on.
     * <p>
     * The walk here is made for a cache - the window advances a bar at a time over the same list,
     * so every window but its newest stretch has been scanned before - and once the cache was
     * actually caching it paid: on minute bars, from about three times faster than a plain scan on
     * a window of five hundred to tens of times faster on wider ones. It first measured slower only
     * because it was broken and rescanned every window whole.
     * <p>
     * Off by default all the same, because it does not answer quite as a plain scan does. A plain
     * scan cannot judge the first few bars of a window, having nothing to their left; the cache
     * judged them earlier, when they sat further in, and remembers. Past those opening bars the two
     * agree exactly - checked window by window over two years of one share - but a level signal
     * read through the cache moves in the third decimal, so runs with it and without it should not
     * be compared as if they were the same measurement.
     * <p>
     * Each locator gets its own cache. A cached locator carries state, and one repository shared
     * between the minimum and the maximum locator would answer each with the other's extremes. That
     * state is also why it cannot simply be switched on under a walk-forward, which runs threads.
     */
    private static ExtremeLocator cached(PivotPointExtremeLocator pivots) {
        if (!Boolean.getBoolean("cache")) {
            return pivots;
        }

        // Only the pivots go behind the cache. Grouping looks across the whole window and cannot be
        // cached in pieces, so it is laid over what comes out - see ProximityGroupingExtremeLocator -
        // and the unsettled tail is cut between the two, where a plain scan leaves it off.
        return pivots.groupingOf(pivots.confirmedOf(new CachingExtremeLocator(pivots.withoutGrouping())));
    }

    private static int[] calculatorPeriods() {
        return new int[]{3, 5, 10, 15, 30};
    }
}
