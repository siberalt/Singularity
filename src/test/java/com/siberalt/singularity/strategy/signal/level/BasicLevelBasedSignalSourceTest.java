package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BasicLevelBasedSignalSourceTest {

    @Test
    void calculatesSignalWhenCurrentPriceIsBetweenSupportAndResistance() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        LevelPair levelPair = new LevelPair(support, resistance);
        double currentPrice = 7.5;

        BasicLevelBasedSignalSource calculator = new BasicLevelBasedSignalSource();
        Signal result = calculator.calculate(levelPair, List.of(createCandle(currentPrice)));

        assertNotNull(result);
        assertTrue(result.confidence() >= -1);
        assertTrue(result.confidence() <= 1);
    }

    @Test
    void calculatesSignalWhenCurrentPriceIsEqualToWeightedPrice() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        LevelPair levelPair = new LevelPair(resistance, support);
        double currentPrice = 7.0; // Weighted price for these levels

        BasicLevelBasedSignalSource calculator = new BasicLevelBasedSignalSource();
        Signal result = calculator.calculate(levelPair, List.of(createCandle(currentPrice)));

        assertNotNull(result);
        assertEquals(0, result.confidence(), 0.1);
    }

    @Test
    void calculatesSignalWhenCurrentPriceIsFarAboveResistance() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        LevelPair levelPair = new LevelPair(resistance, support);

        BasicLevelBasedSignalSource calculator = new BasicLevelBasedSignalSource();
        Signal result = calculator.calculate(levelPair, List.of(createCandle(15.0)));

        assertNotNull(result);
        assertEquals(1.0, result.confidence(), 1e-9);
    }

    @Test
    void calculatesSignalWhenCurrentPriceIsFarBelowSupport() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        LevelPair levelPair = new LevelPair(resistance, support);

        BasicLevelBasedSignalSource calculator = new BasicLevelBasedSignalSource();
        Signal result = calculator.calculate(levelPair, List.of(createCandle(1.0)));

        assertNotNull(result);
        assertEquals(-1.0, result.confidence(), 1e-9);
    }

    @Test
    void calculatesSignalWhenCurrentPriceIsJustBelowResistance() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        LevelPair levelPair = new LevelPair(resistance, support);

        BasicLevelBasedSignalSource calculator = new BasicLevelBasedSignalSource();
        Signal result = calculator.calculate(levelPair, List.of(createCandle(9.9)));

        assertNotNull(result);
        assertTrue(result.confidence() < 0);
        assertTrue(result.confidence() > -1);
    }

    @Test
    void calculatesSignalWhenCurrentPriceIsJustAboveSupport() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        LevelPair levelPair = new LevelPair(resistance, support);

        BasicLevelBasedSignalSource calculator = new BasicLevelBasedSignalSource();
        Signal result = calculator.calculate(levelPair, List.of(createCandle(5.1)));

        assertNotNull(result);
        assertTrue(result.confidence() > 0);
        assertTrue(result.confidence() < 1);
    }

    private Candle createCandle(double price) {
        return Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, price);
    }

    private Level<Double> createLevel(double price, double strength, long indexTo) {
        return new Level<>(new TimePoint(0L), new TimePoint(indexTo), x -> price, strength);
    }
}
