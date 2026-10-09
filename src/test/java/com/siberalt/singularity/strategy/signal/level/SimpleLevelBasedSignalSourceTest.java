package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleLevelBasedSignalSourceTest {
    @Test
    void calculateReturnsNeutralSignalWhenCurrentPriceExceedsResistance() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        LevelPair levelPair = new LevelPair(resistance, support);
        Candle lastCandle = Candle.of(new TimePoint(100, Instant.parse("2024-01-01T00:00:00Z")), 125);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedSignalSource calculator = new SimpleLevelBasedSignalSource();

        Signal result = calculator.calculate(levelPair, recentCandles);

        assertEquals(Signal.NEUTRAL, result);
    }

    @Test
    void calculateReturnsNeutralSignalWhenCurrentPriceFallsBelowSupport() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        LevelPair levelPair = new LevelPair(resistance, support);
        Candle lastCandle = Candle.of(new TimePoint(100, Instant.parse("2024-01-01T00:00:00Z")), 95);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedSignalSource calculator = new SimpleLevelBasedSignalSource();

        Signal result = calculator.calculate(levelPair, recentCandles);

        assertEquals(Signal.NEUTRAL, result);
    }

    @Test
    void calculateReturnsCorrectSignalWhenCurrentPriceIsWithinBounds() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        LevelPair levelPair = new LevelPair(resistance, support);
        Candle lastCandle = Candle.of(new TimePoint(100, Instant.parse("2024-01-01T00:00:00Z")), 110);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedSignalSource calculator = new SimpleLevelBasedSignalSource();

        Signal result = calculator.calculate(levelPair, recentCandles);

        assertEquals(new Signal(0.0, 0.0), result);
    }

    @Test
    void calculateReturnsSignalCloserToResistance() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        LevelPair levelPair = new LevelPair(resistance, support);
        Candle lastCandle = Candle.of(new TimePoint(100, Instant.parse("2024-01-01T00:00:00Z")), 118);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedSignalSource calculator = new SimpleLevelBasedSignalSource();

        Signal result = calculator.calculate(levelPair, recentCandles);

        assertEquals(new Signal(-0.8, -0.8), result);
    }

    @Test
    void calculateReturnsSignalCloserToSupport() {
        Level<Double> resistance = new Level<>(1, 10, index -> 120.0);
        Level<Double> support = new Level<>(1, 10, index -> 100.0);
        LevelPair levelPair = new LevelPair(resistance, support);
        Candle lastCandle = Candle.of(new TimePoint(100, Instant.parse("2024-01-01T00:00:00Z")), 102);

        List<Candle> recentCandles = List.of(lastCandle);
        SimpleLevelBasedSignalSource calculator = new SimpleLevelBasedSignalSource();

        Signal result = calculator.calculate(levelPair, recentCandles);

        assertEquals(new Signal(0.8, 0.8), result);
    }
}
