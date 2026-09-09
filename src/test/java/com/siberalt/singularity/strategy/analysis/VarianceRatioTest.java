package com.siberalt.singularity.strategy.analysis;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VarianceRatioTest {
    private static final Instant START = Instant.parse("2021-06-03T10:00:00Z");

    private final VarianceRatio varianceRatio = new VarianceRatio();

    @Test
    void sitsAtOneForARandomWalk() {
        Random random = new Random(7);
        List<Double> prices = new ArrayList<>();
        double price = 100;

        for (int i = 0; i < 20_000; i++) {
            prices.add(price);
            price *= 1 + 0.01 * random.nextGaussian();
        }

        assertEquals(1.0, varianceRatio.measure(candles(prices), 10), 0.15);
    }

    /**
     * A price whose moves half-repeat the previous one, which is what a trending instrument looks
     * like. Not a straight line: a constant drift has no variance in its returns at all, so it
     * measures nothing - trend as a signal means moves that agree with each other, not a ramp.
     */
    @Test
    void risesAboveOneWhenMovesCarryOn() {
        assertTrue(
            varianceRatio.measure(candles(autocorrelated(0.5)), 10) > 2,
            "moves that repeat should push the ratio well above one"
        );
    }

    private List<Double> autocorrelated(double persistence) {
        Random random = new Random(11);
        List<Double> prices = new ArrayList<>();
        double price = 100;
        double previousReturn = 0;

        for (int i = 0; i < 20_000; i++) {
            prices.add(price);
            previousReturn = persistence * previousReturn + 0.01 * random.nextGaussian();
            price *= 1 + previousReturn;
        }

        return prices;
    }

    /** A price that comes straight back: the long move is smaller than the short ones suggest. */
    @Test
    void fallsBelowOneWhenMovesReverse() {
        List<Double> prices = new ArrayList<>();

        for (int i = 0; i < 2_000; i++) {
            prices.add(i % 2 == 0 ? 100.0 : 101.0);
        }

        assertTrue(varianceRatio.measure(candles(prices), 10) < 0.1, "a pure zigzag should be far below one");
    }

    @Test
    void refusesAHorizonOfOneBar() {
        List<Candle> candles = candles(List.of(100.0, 101.0, 102.0));

        assertThrows(IllegalArgumentException.class, () -> varianceRatio.measure(candles, 1));
    }

    @Test
    void reportsNothingForAPriceThatNeverMoves() {
        List<Double> flat = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            flat.add(100.0);
        }

        assertEquals(0, varianceRatio.measure(candles(flat), 5));
    }

    private List<Candle> candles(List<Double> prices) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < prices.size(); i++) {
            Quotation price = Quotation.of(prices.get(i));
            candles.add(new Candle("TEST", new TimePoint(START.plusSeconds(i * 60L)), price, price, price, price, 100));
        }

        return candles;
    }
}
