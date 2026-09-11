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
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.extreme.cache.CachingExtremeLocator;
import com.siberalt.singularity.strategy.level.linear.StatelessClusterLevelDetector;
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
    private static final String DEFAULT_INSTRUMENT = "TMOS";
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

    public static void main(String[] args) throws IOException {
        String instrumentUid = args.length > 0 ? args[0] : DEFAULT_INSTRUMENT;
        Instant from = Instant.parse(args.length > 1 ? args[1] : DEFAULT_FROM);
        Instant to = Instant.parse(args.length > 2 ? args[2] : DEFAULT_TO);
        List<String> intervals = args.length > 3
            ? List.of(args).subList(3, args.length)
            : List.of("MIN_1", "HOUR", "DAY");

        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );
        SqliteCandleRepository candleRepository = new SqliteCandleRepositoryFactory()
            .create(ConfigFacade.of(configuration).getAsString("dbPath"));

        List<Candle> minuteCandles = candleRepository.findAfterOrEqual(instrumentUid, from, Integer.MAX_VALUE)
            .stream()
            .filter(candle -> candle.getTime().isBefore(to))
            .toList();

        System.out.printf("%s: %d minute bars, %s .. %s%n",
            instrumentUid, minuteCandles.size(), from, to);

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

        // A minute series runs to hundreds of thousands of bars and every one of them costs the
        // calculator a pass over its whole window; a wider one is small enough to take whole.
        int stride = Integer.parseInt(System.getProperty("stride",
            interval == CandleInterval.MIN_1 ? "10" : "1"));

        System.out.printf("%n===== %s : %d bars, lookback %d, stride %d =====%n",
            interval, candles.size(), lookback, stride);

        if (candles.size() < lookback + 100) {
            System.out.println("  not enough bars to say anything");
            return;
        }

        System.out.printf("%-8s %6s %6s | %s%n", "period", "fired", "lag1",
            "horizon: corr(exec) / edge bp vs hold bp / n   (same-bar corr)");

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
                default -> new SlopeUpsideCalculator(period);
            };

            if (Boolean.getBoolean("invert")) {
                calculator = new InvertedUpsideCalculator(calculator);
            }
            long startedAt = System.currentTimeMillis();
            PredictivenessReport report = new SignalPredictiveness()
                .setLookbackCandles(lookback)
                .setStride(stride)
                .measure(candles, calculator);
            long elapsed = System.currentTimeMillis() - startedAt;

            StringBuilder line = new StringBuilder();

            for (PredictivenessReport.HorizonStat stat : report.horizons()) {
                line.append(String.format(
                    "  h=%-3d %+.3f%s/%+7.1f vs %+7.1f/%-6d (%+.3f)",
                    stat.horizon(),
                    stat.executableCorrelation(),
                    stat.isAboveNoise() ? "*" : " ",
                    stat.edgeBasisPoints(),
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
                    default -> "slp";
                },
                period,
                100.0 * report.firedBars() / Math.max(1, report.bars()),
                report.lag1Autocorrelation(),
                line,
                elapsed / 1000
            );
        }

        System.out.println("  * = outside the noise floor; overlapping horizons make even that optimistic");
    }

    /**
     * Puts a locator behind the range cache unless {@code -Dcache=false} turns it off.
     * <p>
     * Worth it here because of how this measurement walks: the window advances a bar at a time over
     * the same list, so all but the newest stretch of every window has been scanned already, and
     * without the cache a level calculator re-reads its whole lookback on every one of thousands of
     * bars. That is what put an hourly measurement out of reach.
     * <p>
     * It is an approximation, not a free lunch, and the reason to keep the switch. A locator that
     * needs bars either side of a candle to call it an extreme cannot see the ones at the edge of
     * the short stretch it is handed, so a cached run and a plain one need not agree exactly -
     * compare the two on a series small enough to run both before trusting the cached numbers.
     * <p>
     * Each locator gets its own cache. A cached locator carries state, and one repository shared
     * between the minimum and the maximum locator would answer each with the other's extremes.
     */
    private static ExtremeLocator cached(ExtremeLocator baseLocator) {
        if (!Boolean.parseBoolean(System.getProperty("cache", "true"))) {
            return baseLocator;
        }

        return new CachingExtremeLocator(baseLocator);
    }

    private static int[] calculatorPeriods() {
        return new int[]{3, 5, 10, 15, 30};
    }
}
