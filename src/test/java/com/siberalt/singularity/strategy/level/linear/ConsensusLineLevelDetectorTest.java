package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.math.LinearFunction;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
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
