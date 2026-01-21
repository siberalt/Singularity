package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BasicLevelBasedUpsideCalculatorTest {

    @Test
    void calculatesUpsideWhenCurrentPriceIsBetweenSupportAndResistance() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        double currentPrice = 7.5;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(currentPrice)));

        assertNotNull(result);
        assertTrue(result.signal() >= -1);
        assertTrue(result.signal() <= 1);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsEqualToWeightedPrice() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);
        double currentPrice = 7.0; // Weighted price for these levels

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(currentPrice)));

        assertNotNull(result);
        assertEquals(0, result.signal(), 0.1);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsFarAboveResistance() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(15.0)));

        assertNotNull(result);
        assertEquals(1.0, result.signal(), 1e-9);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsFarBelowSupport() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(1.0)));

        assertNotNull(result);
        assertEquals(-1.0, result.signal(), 1e-9);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsJustBelowResistance() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(9.9)));

        assertNotNull(result);
        assertTrue(result.signal() < 0);
        assertTrue(result.signal() > -1);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsJustAboveSupport() {
        Level<Double> resistance = createLevel(10.0, 5.0, 100);
        Level<Double> support = createLevel(5.0, 3.0, 50);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(5.1)));

        assertNotNull(result);
        assertTrue(result.signal() > 0);
        assertTrue(result.signal() < 1);
    }

    private Candle createCandle(double price) {
        return Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, price);
    }

    private Level<Double> createLevel(double price, double strength, long indexTo) {
        return new Level<>(null, null, 0L, indexTo, x -> price, strength);
    }
}
