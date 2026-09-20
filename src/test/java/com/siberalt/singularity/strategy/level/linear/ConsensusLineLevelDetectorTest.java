package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.math.LinearFunction;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsensusLineLevelDetectorTest {
    private static final Instant START = Instant.parse("2023-01-02T07:00:00Z");

    @Test
    void findsTheSlopedLineTheLowsLieOn() {
        // Lows on a line rising by two a bar, each a little off it, plus three lows nowhere near it.
        List<Candle> candles = candles(
            low(0, 100.4), low(10, 120.0), low(20, 139.6), low(30, 160.2), low(40, 180.0),
            low(5, 200.0), low(15, 60.0), low(25, 250.0)
        );

        List<Level<Double>> levels = detector(candles).detect(candles);

        assertFalse(levels.isEmpty());
        Level<Double> strongest = levels.getFirst();

        assertEquals(5, strongest.touchesCount());
        assertEquals(2.0, ((LinearFunction<Double>) strongest.function()).getSlope(), 0.05);
        assertEquals(0, strongest.indexFrom());
        assertEquals(40, strongest.indexTo());
    }

    /**
     * The incremental detector fixes a direction from the first two extremes it meets and can only
     * extend it with the next one in order, so two outliers at the front become a level of their own -
     * on this very series it reports a line of slope minus thirty before finding the real one. Worse,
     * when the outliers are interleaved with the level's own lows rather than ahead of them, every
     * outlier breaks the run and the line is never found at all - which is the series of
     * {@link #findsTheSlopedLineTheLowsLieOn}, where the incremental detector returns four two-point
     * lines and nothing else. Here the line is chosen by how many lows agree with it, so the order
     * they arrive in decides nothing.
     */
    @Test
    void theFirstTwoLowsDoNotDecideTheDirection() {
        // The first two lows suggest a line falling steeply; the next five say it rises by two a bar.
        List<Candle> candles = candles(
            low(0, 300.0), low(2, 240.0),
            low(10, 120.2), low(20, 139.8), low(30, 160.1), low(40, 179.9), low(50, 200.2)
        );

        List<Level<Double>> levels = detector(candles).detect(candles);

        Level<Double> strongest = levels.getFirst();

        assertEquals(5, strongest.touchesCount());
        assertEquals(2.0, ((LinearFunction<Double>) strongest.function()).getSlope(), 0.05);
        assertEquals(10, strongest.indexFrom());
    }

    @Test
    void refitsTheLineOverEveryLowThatAgreesWithIt() {
        // A pair whose own line is slightly off the crowd: the refit has to pull it onto the crowd.
        List<Candle> candles = candles(
            low(0, 100.0), low(10, 110.0), low(20, 120.0), low(30, 130.0), low(40, 140.0)
        );

        Level<Double> level = detector(candles).detect(candles).getFirst();

        assertEquals(5, level.touchesCount());
        assertEquals(1.0, ((LinearFunction<Double>) level.function()).getSlope(), 1e-6);
        assertEquals(100.0, level.function().apply(0.0), 1e-6);
    }

    @Test
    void aWiderToleranceTakesInWhatANarrowOneLeavesOut() {
        List<Candle> candles = candles(
            low(0, 100.0), low(10, 110.0), low(20, 120.0), low(30, 133.0), low(40, 140.0)
        );

        int narrow = detector(candles).setVolatilityTolerance(window -> 1, 1).detect(candles).getFirst().touchesCount();
        int wide = detector(candles).setVolatilityTolerance(window -> 1, 5).detect(candles).getFirst().touchesCount();

        assertEquals(4, narrow);
        assertEquals(5, wide);
    }

    @Test
    void findsASecondLevelAmongTheLowsTheFirstDidNotClaim() {
        List<Candle> candles = candles(
            low(0, 100.0), low(10, 110.0), low(20, 120.0), low(30, 130.0),
            low(5, 200.0), low(15, 200.0), low(25, 200.0), low(35, 200.0)
        );

        List<Level<Double>> levels = detector(candles).detect(candles);

        assertEquals(2, levels.size());
        assertEquals(4, levels.get(0).touchesCount());
        assertEquals(4, levels.get(1).touchesCount());
    }

    @Test
    void keepsNothingBetweenCalls() {
        List<Candle> first = candles(low(0, 100.0), low(10, 110.0), low(20, 120.0), low(30, 130.0));
        List<Candle> second = candles(low(0, 100.0), low(10, 90.0), low(20, 80.0), low(30, 70.0));
        ConsensusLineLevelDetector detector = detector(first);

        Level<Double> before = detector.detect(first).getFirst();
        Level<Double> other = detector.detect(second).getFirst();
        Level<Double> again = detector.detect(first).getFirst();

        assertEquals(1.0, ((LinearFunction<Double>) before.function()).getSlope(), 1e-6);
        assertEquals(-1.0, ((LinearFunction<Double>) other.function()).getSlope(), 1e-6);
        assertEquals(1.0, ((LinearFunction<Double>) again.function()).getSlope(), 1e-6);
    }

    @Test
    void saysNothingWhenThereAreTooFewLows() {
        List<Candle> candles = candles(low(0, 100.0), low(10, 110.0));

        assertTrue(detector(candles).detect(candles).isEmpty());
        assertTrue(detector(candles).detect(List.of()).isEmpty());
        assertTrue(detector(candles).detect(null).isEmpty());
    }

    /**
     * A least-squares line runs through the middle of the lows, so half of them sit below it - that is
     * an axis of symmetry, not a support, and on a rising series it can even point down. Asked for an
     * envelope, the detector has to put the line under its own points.
     */
    @Test
    void anEnvelopeRestsUnderItsLowsInsteadOfRunningThroughTheMiddleOfThem() {
        List<Candle> candles = candles(
            low(0, 100.0), low(10, 109.5), low(20, 120.5), low(30, 129.5), low(40, 140.5)
        );

        Level<Double> middle = detector(candles).detect(candles).getFirst();
        Level<Double> resting = detector(candles).setEnvelope(true).detect(candles).getFirst();

        // The middle line has the tolerance on both sides of it; the envelope has it on one, so at the
        // same tolerance it accepts less - here four of the five lows, the fifth being 1.1 above.
        assertEquals(5, middle.touchesCount());
        assertEquals(4, resting.touchesCount());

        for (Candle candle : candles) {
            double distance = candle.getLowAsDouble() - resting.function().apply((double) candle.getIndex());

            assertTrue(distance >= -1e-9, "a low sits " + distance + " below the envelope");
        }

        // The middle line has lows on both sides of it; the envelope touches its lowest one.
        assertTrue(candles.stream().anyMatch(candle ->
            candle.getLowAsDouble() < middle.function().apply((double) candle.getIndex()) - 1e-9));
        assertEquals(0.0, candles.stream()
            .mapToDouble(candle -> candle.getLowAsDouble() - resting.function().apply((double) candle.getIndex()))
            .min()
            .orElseThrow(), 1e-9);
    }

    /**
     * The point of the envelope: a line that has to lie under rising lows cannot itself fall, because on
     * the right there would be nothing holding it up.
     */
    @Test
    void anEnvelopeCannotPointAgainstTheLowsItRestsOn() {
        // Lows rising by one a bar, with two early ones low enough that a middle line could tilt down.
        List<Candle> candles = candles(
            low(0, 101.0), low(5, 100.0), low(10, 110.0), low(20, 120.0), low(30, 130.0), low(40, 140.0)
        );

        Level<Double> resting = detector(candles).setEnvelope(true).detect(candles).getFirst();

        assertTrue(((LinearFunction<Double>) resting.function()).getSlope() > 0,
            "the envelope slopes " + ((LinearFunction<Double>) resting.function()).getSlope());
    }

    /**
     * A line can be held up entirely by lows from the far side of the window: it passes under them
     * honestly and still points the wrong way for today. Asked for a fresh touch, the detector has to
     * prefer the line that something recent stands on.
     */
    @Test
    void prefersTheLineWithARecentTouch() {
        // Five old lows falling, four recent ones rising, so the old line wins on points alone; the
        // window ends at bar 70.
        List<Candle> candles = candles(
            low(0, 140.0), low(5, 130.0), low(10, 120.0), low(15, 110.0), low(20, 100.0),
            low(40, 100.0), low(50, 110.0), low(60, 120.0), low(70, 130.0)
        );

        List<Level<Double>> without = detector(candles).setEnvelope(true).detect(candles);
        List<Level<Double>> with = detector(candles).setEnvelope(true).setFreshTouchWithin(5).detect(candles);

        // Both lines are real and both are reported: the falling one has the most points, the rising one
        // the most strength, which is why it is first either way.
        assertEquals(2, without.size());
        assertEquals(-2.0, slopeOf(without.get(1)), 1e-6);

        // Asked for a fresh touch, the line whose newest low is fifty bars old stops being a level.
        assertEquals(1, with.size());
        assertEquals(1.0, slopeOf(with), 1e-6);
        assertTrue(with.getFirst().indexTo() >= 40);
    }

    /** Bars, not index units: an hourly bar carries the index of its minute, sixty apart from the next. */
    @Test
    void freshnessIsCountedInBarsWhateverTheIndicesAre() {
        List<Candle> minutes = candles(
            low(0, 140.0), low(5, 130.0), low(10, 120.0), low(15, 110.0), low(20, 100.0),
            low(40, 100.0), low(50, 110.0), low(60, 120.0), low(70, 130.0)
        );
        List<Candle> hours = candles(
            low(0, 140.0), low(300, 130.0), low(600, 120.0), low(900, 110.0), low(1200, 100.0),
            low(2400, 100.0), low(3000, 110.0), low(3600, 120.0), low(4200, 130.0)
        );

        List<Level<Double>> onMinutes = detector(minutes).setEnvelope(true).setFreshTouchWithin(5).detect(minutes);
        List<Level<Double>> onHours = detector(hours).setEnvelope(true).setFreshTouchWithin(5).detect(hours);

        // The same nine bars, sixty index units apart instead of one: the same level is dropped as stale
        // and the same one survives, its slope stretched by the spacing.
        assertEquals(1, onMinutes.size());
        assertEquals(1, onHours.size());
        assertEquals(slopeOf(onMinutes), slopeOf(onHours) * 60, 1e-6);
    }

    private static double slopeOf(List<Level<Double>> levels) {
        return slopeOf(levels.getFirst());
    }

    private static double slopeOf(Level<Double> level) {
        return ((LinearFunction<Double>) level.function()).getSlope();
    }

    /**
     * The tolerance belongs to the bar, not to the window: a low is allowed to miss the line by as much
     * as its own time was volatile. Here every bar but the last is measured tightly, and the last one
     * loosely - the same three-unit miss is an outlier in the first case and a touch in the second.
     */
    @Test
    void judgesEveryLowByTheVolatilityOfItsOwnTime() {
        // The odd low sits in the middle of the others, where no tilt of the line can reach it without
        // losing both ends - so whether it belongs is decided by its tolerance and nothing else.
        List<Candle> candles = candles(
            low(0, 100.0), low(10, 100.0), low(20, 100.0), low(30, 100.0), low(40, 100.0), low(25, 98.5));

        Level<Double> tight = detector(candles).detect(candles).getFirst();

        assertEquals(5, tight.touchesCount(), "a miss of one and a half is not a touch when a volatility is one");

        ConsensusLineLevelDetector local = ConsensusLineLevelDetector.createSupport(window -> window)
            .setVolatilityTolerance(looseOnTheLastBar(), 1);

        assertEquals(6, local.detect(candles).getFirst().touchesCount(),
            "the same miss is a touch when that bar's own volatility is five");
    }

    /** A window whose bars are all worth one volatility, except the last, which is worth five. */
    private static VolatilityCalculator looseOnTheLastBar() {
        return new VolatilityCalculator() {
            @Override
            public double calculate(List<Candle> candles) {
                return 1;
            }

            @Override
            public double[] profile(List<Candle> candles) {
                double[] profile = new double[candles.size()];

                Arrays.fill(profile, 1);
                profile[profile.length - 1] = 5;

                return profile;
            }
        };
    }

    @Test
    void refusesSettingsThatCannotMeanAnything() {
        ConsensusLineLevelDetector detector = ConsensusLineLevelDetector.createSupport(candles -> List.of());

        assertThrows(IllegalArgumentException.class, () -> detector.setVolatilityTolerance(window -> 1, 0));
        assertThrows(IllegalArgumentException.class, () -> detector.setMinPoints(1));
        assertThrows(IllegalArgumentException.class, () -> detector.setMaxLevels(0));
    }

    /** Every candle is an extreme here, so the test decides which lows the detector is given. */
    private ConsensusLineLevelDetector detector(List<Candle> extremes) {
        ExtremeLocator all = candles -> candles;

        return ConsensusLineLevelDetector.createSupport(all)
            // A tolerance of one price unit, whatever the window looks like: these series are made up
            // of exact numbers, and an ATR of them would only obscure what the test is about.
            .setVolatilityTolerance(window -> 1, 1);
    }

    private record Low(long index, double price) {
    }

    private static Low low(long index, double price) {
        return new Low(index, price);
    }

    private static List<Candle> candles(Low... lows) {
        List<Candle> candles = new ArrayList<>();

        for (Low low : lows) {
            Quotation price = Quotation.of(low.price());
            candles.add(new Candle(1, new TimePoint(low.index(), START.plusSeconds(3600L * low.index())),
                price, price, price, price, 1));
        }

        return candles;
    }
}
