package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleLevelBasedUpsideCalculatorTest {
    @Test
    void calculateReturnsNeutralUpsideWhenCurrentPriceExceedsResistance() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        Candle lastCandle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 125).setIndex(100L);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedUpsideCalculator calculator = new SimpleLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(resistance, support, recentCandles);

        assertEquals(Upside.NEUTRAL, result);
    }

    @Test
    void calculateReturnsNeutralUpsideWhenCurrentPriceFallsBelowSupport() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        Candle lastCandle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 95).setIndex(100L);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedUpsideCalculator calculator = new SimpleLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(resistance, support, recentCandles);

        assertEquals(Upside.NEUTRAL, result);
    }

    @Test
    void calculateReturnsCorrectUpsideWhenCurrentPriceIsWithinBounds() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        Candle lastCandle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 110).setIndex(100L);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedUpsideCalculator calculator = new SimpleLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(resistance, support, recentCandles);

        assertEquals(new Upside(0.0, 0.0), result);
    }

    @Test
    void calculateReturnsUpsideCloserToResistance() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        Candle lastCandle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 118).setIndex(100L);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedUpsideCalculator calculator = new SimpleLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(resistance, support, recentCandles);

        assertEquals(new Upside(-0.8, -0.8), result);
    }

    @Test
    void calculateReturnsUpsideCloserToSupport() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        Candle lastCandle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 102).setIndex(100L);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedUpsideCalculator calculator = new SimpleLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(resistance, support, recentCandles);

        assertEquals(new Upside(0.8, 0.8), result);
    }
}
