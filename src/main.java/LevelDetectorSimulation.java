import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ProminentExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.ConsensusLineLevelDetector;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Every position of the search window, computed once, for the page to step through.
 * <p>
 * The detector is the one the contest picked: of the four compared on sixteen instruments over three
 * periods, only the consensus fit taken as an envelope, fed prominent pivots and made to require a fresh
 * touch, caught more future lows than a control line shifted two ATR away - pooled lift 1.123 +- 0.038
 * against the control's 0.985. The plain consensus fit and both cluster variants were level with theirs.
 * <p>
 * The window is 1760 hourly bars, half a year of trading hours, which is what was measured: against 1000
 * and 1500 it kept the same lines but gave them six to nine touches instead of four. It slides along the
 * whole period in steps of {@link #STEP} bars, and each position is a frame - the levels found in that
 * window and nothing later. Each frame is also given {@link #LOOKAHEAD} bars past the window's end: the
 * future those levels did not see, where the lines carry on as projections and the lows of that stretch
 * are marked as caught or missed. That is the question the levels can answer; the money ones they could
 * not - seven protocols on them failed.
 * <p>
 * The frames go to one file, {@link #OUTPUT}, which {@code PriceChart.html} steps through with the arrow
 * keys. Nothing is recomputed while stepping; the whole sweep is done here, in parallel, once.
 */
public class LevelDetectorSimulation {
    // Our id for TMOS - what its candles are kept under.
    private static final long INSTRUMENT_ID = 7;
    private static final Instant FROM = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2025-12-01T00:00:00Z");
    /** The bars the detector sees. The contest measured hourly; minutes were never tested. */
    private static final CandleInterval INTERVAL = CandleInterval.HOUR;
    /** How far to either side a bar has to be the lowest to count as a pivot, in bars of INTERVAL. */
    private static final int PIVOT_VICINITY = 14;
    /** How deep the ground around a pivot has to fall away from it, in volatilities. */
    private static final double PROMINENCE = 0.5;
    /** A level is only kept if something touched it within this many bars of the end. */
    private static final long FRESH_BARS = 200;
    /** How far from the line a low may sit and still be a touch of it, in volatilities. */
    private static final double TOLERANCE = 1;
    /** How many touches make a level. The pictures looked cleaner at 4 or 5, at the cost of coverage. */
    private static final int MIN_TOUCHES = 3;
    private static final int MAX_LEVELS = 10;
    /**
     * The search window, in bars of INTERVAL. Nine hundred - about three and a half months of trading
     * hours - is what the sweep settled on: against 1760 it caught more of the future lows in thirteen
     * of sixteen detectors, and the advantage held at every freshness, so it is the window itself that
     * matters and not the age of the last touch.
     */
    private static final int WINDOW = 900;
    /** How far the window moves from one frame to the next. */
    private static final int STEP = 100;
    /** Bars drawn past the window's end - the future the levels of that frame did not see. */
    private static final int LOOKAHEAD = 440;
    /** How near a projected level a later low must fall to count as caught, in volatilities. */
    private static final double CATCH_TOLERANCE = 0.5;
    private static final Path OUTPUT = Paths.get("src/main/resources/presenter/google/LevelFrames.json");

    /** One position of the window: what was found in it, and how wide a bar was while it was found. */
    private record Frame(int from, int to, double volatility, List<Line> levels) {
    }

    /**
     * A level as the page draws it: the rows it spans, its price at the first of them, and how much it
     * gains per unit of candle index. Rows are not evenly spaced in index - nights and weekends are gaps
     * - so the page walks the line by index rather than interpolating between its ends.
     */
    private record Line(int from, int to, double price, double slope, int touches) {
    }

    public static void main(String[] args) throws IOException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepositoryFactory sqliteCandleRepositoryFactory = new SqliteCandleRepositoryFactory();
        SqliteCandleRepository candleRepository = sqliteCandleRepositoryFactory.create(dbPath);

        List<Candle> candles = candleRepository.getPeriod(INSTRUMENT_ID, FROM, TO);
        List<Candle> bars = new CandleAggregator().aggregate(candles, INTERVAL);

        if (bars.size() < WINDOW) {
            throw new IllegalStateException(
                "The period holds %d bars of %s, less than the window of %d".formatted(bars.size(), INTERVAL, WINDOW));
        }

        int frames = (bars.size() - WINDOW) / STEP + 1;
        long startedAt = System.currentTimeMillis();
        List<Frame> sweep = IntStream.range(0, frames)
            .parallel()
            .mapToObj(frame -> frameOf(bars, frame * STEP))
            .toList();

        write(bars, sweep);

        System.out.printf(Locale.ROOT, "%d bars of %s, %d frames of %d bars every %d, %.1f s%n",
            bars.size(), INTERVAL, frames, WINDOW, STEP, (System.currentTimeMillis() - startedAt) / 1000.0);
        System.out.printf(Locale.ROOT, "levels per frame: %.1f on average, %d at most%n",
            sweep.stream().mapToInt(frame -> frame.levels().size()).average().orElse(0),
            sweep.stream().mapToInt(frame -> frame.levels().size()).max().orElse(0));
        System.out.println("written to " + OUTPUT);
    }

    /** One position of the window: detect in it, and say where the lines run in rows of the whole series. */
    private static Frame frameOf(List<Candle> bars, int from) {
        List<Candle> window = bars.subList(from, from + WINDOW);
        List<Line> lines = new ArrayList<>();

        for (Level<Double> level : supportDetector(prominentMinimums()).detect(window)) {
            lineOf(level, bars, from).ifPresent(lines::add);
        }

        return new Frame(from, from + WINDOW, new ATRVolatilityCalculator().calculate(window), lines);
    }

    /**
     * Where a level runs, in rows of the whole series. Its own bounds are candle indices, so the rows are
     * the ones of the window whose candles fall inside them.
     */
    private static Optional<Line> lineOf(Level<Double> level, List<Candle> bars, int from) {
        int first = -1;
        int last = -1;

        for (int row = from; row < from + WINDOW; row++) {
            long index = bars.get(row).getIndex();

            if (index >= level.indexFrom() && index <= level.indexTo()) {
                first = first < 0 ? row : first;
                last = row;
            }
        }

        if (first < 0) {
            return Optional.empty();
        }

        double indexFrom = bars.get(first).getIndex();
        double indexTo = bars.get(last).getIndex();
        double priceFrom = level.function().apply(indexFrom);
        // Taken across the whole span rather than over one unit: the intercept sits millions of units
        // away, and the difference of two such numbers keeps no digits of a slope this small.
        double slope = indexTo > indexFrom
            ? (level.function().apply(indexTo) - priceFrom) / (indexTo - indexFrom)
            : 0;

        return Optional.of(new Line(first, last, priceFrom, slope, level.touchesCount()));
    }

    /**
     * The winner of the contest: the consensus fit resting under its lowest touch, on pivots that stand
     * out from the ground around them, kept only while the price still remembers them.
     */
    private static LevelDetector supportDetector(ExtremeLocator minExtremeLocator) {
        VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator();

        return ConsensusLineLevelDetector.createSupport(minExtremeLocator)
            .setVolatilityTolerance(volatilityCalculator, TOLERANCE)
            .setEnvelope(true)
            .setFreshTouchWithin(FRESH_BARS)
            .setMinPoints(MIN_TOUCHES)
            .setMaxLevels(MAX_LEVELS);
    }

    /** For resistances, mirror it: {@code createResistance} over {@code ofMaximums} of the same pair. */
    private static ExtremeLocator prominentMinimums() {
        return ProminentExtremeLocator.ofMinimums(
            PivotPointExtremeLocator.ofMinimums(PIVOT_VICINITY),
            new ATRVolatilityCalculator(),
            PROMINENCE
        );
    }

    /**
     * The series once, the frames after it. The page needs the candle index of every row to walk a line
     * across the gaps, and the lows to mark; everything else it derives.
     */
    private static void write(List<Candle> bars, List<Frame> frames) throws IOException {
        Set<Long> lows = new HashSet<>();
        prominentMinimums().locate(bars).forEach(low -> lows.add(low.getIndex()));

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(OUTPUT))) {
            out.printf(Locale.ROOT, "{%n  \"instrument\": %d,%n  \"interval\": \"%s\",%n", INSTRUMENT_ID, INTERVAL);
            out.printf(Locale.ROOT, "  \"window\": %d,%n  \"step\": %d,%n  \"lookahead\": %d,%n  \"catchTolerance\": %s,%n",
                WINDOW, STEP, LOOKAHEAD, CATCH_TOLERANCE);
            out.printf(Locale.ROOT,
                "  \"settings\": {\"vicinity\": %d, \"prominence\": %s, \"tolerance\": %s, \"fresh\": %d, \"touches\": %d, \"maxLevels\": %d},%n",
                PIVOT_VICINITY, PROMINENCE, TOLERANCE, FRESH_BARS, MIN_TOUCHES, MAX_LEVELS);

            out.print("  \"times\": [");
            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.printf("\"%s\"", bars.get(row).getTime());
            }

            out.printf("],%n  \"index\": [");
            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(bars.get(row).getIndex());
            }

            out.printf("],%n  \"price\": [");
            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.printf(Locale.ROOT, "%.4f", bars.get(row).getCloseAsDouble());
            }

            out.printf("],%n  \"lows\": [");
            int written = 0;
            for (int row = 0; row < bars.size(); row++) {
                if (lows.contains(bars.get(row).getIndex())) {
                    out.print(written++ == 0 ? "" : ",");
                    out.printf(Locale.ROOT, "[%d,%.4f]", row, bars.get(row).getLowAsDouble());
                }
            }

            out.printf("],%n  \"frames\": [%n");
            for (int at = 0; at < frames.size(); at++) {
                Frame frame = frames.get(at);
                out.printf(Locale.ROOT, "    {\"from\": %d, \"to\": %d, \"volatility\": %.4f, \"levels\": [",
                    frame.from(), frame.to(), frame.volatility());

                for (int line = 0; line < frame.levels().size(); line++) {
                    Line level = frame.levels().get(line);
                    out.print(line == 0 ? "" : ",");
                    out.printf(Locale.ROOT, "{\"from\": %d, \"to\": %d, \"price\": %.4f, \"slope\": %.10g, \"touches\": %d}",
                        level.from(), level.to(), level.price(), level.slope(), level.touches());
                }

                out.printf("]}%s%n", at == frames.size() - 1 ? "" : ",");
            }

            out.printf("  ]%n}%n");
        }
    }
}
