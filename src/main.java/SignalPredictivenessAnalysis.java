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
import com.siberalt.singularity.strategy.upside.SlopeUpsideCalculator;
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
        int stride = interval == CandleInterval.MIN_1 ? 10 : 1;

        System.out.printf("%n===== %s : %d bars, lookback %d, stride %d =====%n",
            interval, candles.size(), lookback, stride);

        if (candles.size() < lookback + 100) {
            System.out.println("  not enough bars to say anything");
            return;
        }

        System.out.printf("%-8s %6s %6s | %s%n", "period", "fired", "lag1",
            "horizon: corr(exec) / edge bp vs hold bp / n   (same-bar corr)");

        for (int period : calculatorPeriods()) {
            UpsideCalculator calculator = new SlopeUpsideCalculator(period);
            PredictivenessReport report = new SignalPredictiveness()
                .setLookbackCandles(lookback)
                .setStride(stride)
                .measure(candles, calculator);

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
                "slope%-3d %5.1f%% %+.3f |%s%n",
                period,
                100.0 * report.firedBars() / Math.max(1, report.bars()),
                report.lag1Autocorrelation(),
                line
            );
        }

        System.out.println("  * = outside the noise floor; overlapping horizons make even that optimistic");
    }

    private static int[] calculatorPeriods() {
        return new int[]{3, 5, 10, 15, 30};
    }
}
