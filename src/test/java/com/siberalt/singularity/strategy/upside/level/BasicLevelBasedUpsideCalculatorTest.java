package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BasicLevelBasedUpsideCalculatorTest {
    @Test
    void calculatesUpsideWhenCurrentPriceIsBetweenSupportAndResistance() {
        Level<Double> resistance = createLevelMock(10.0, 5.0, 100);
        Level<Double> support = createLevelMock(5.0, 3.0, 50);
        double currentPrice = 7.5;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertTrue(result.signal() >= -1);
        assertTrue(result.signal() <= 1);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsEqualToWeightedPrice() {
        Level<Double> resistance = createLevelMock(10.0, 5.0, 100);
        Level<Double> support = createLevelMock(5.0, 3.0, 50);
        double currentPrice = 7.0; // Weighted price for these levels

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertEquals(0, result.signal(), 0.1);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsFarAboveResistance() {
        Level<Double> resistance = createLevelMock(10.0, 5.0, 100);
        Level<Double> support = createLevelMock(5.0, 3.0, 50);
        double currentPrice = 15.0;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertEquals(1.0, result.signal(), 1e-9);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsFarBelowSupport() {
        Level<Double> resistance = createLevelMock(10.0, 5.0, 100);
        Level<Double> support = createLevelMock(5.0, 3.0, 50);
        double currentPrice = 1.0;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertEquals(-1.0, result.signal(), 1e-9);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsJustBelowResistance() {
        Level<Double> resistance = createLevelMock(10.0, 5.0, 100);
        Level<Double> support = createLevelMock(5.0, 3.0, 50);
        double currentPrice = 9.9;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertTrue(result.signal() < 0);
        assertTrue(result.signal() > -1);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsJustAboveSupport() {
        Level<Double> resistance = createLevelMock(10.0, 5.0, 100);
        Level<Double> support = createLevelMock(5.0, 3.0, 50);
        double currentPrice = 5.1;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertTrue(result.signal() > 0);
        assertTrue(result.signal() < 1);
    }

    private Level<Double> createLevelMock(double price, double strength, long indexTo) {
        Level<Double> level = mock(Level.class);
        when(level.getStrength()).thenReturn(strength);
        when(level.getFunction()).thenReturn(x -> price);
        when(level.getTimeFrom()).thenReturn(null);
        when(level.getTimeTo()).thenReturn(null);
        when(level.getIndexFrom()).thenReturn(0L);
        when(level.getIndexTo()).thenReturn(indexTo);
        return level;
    }
}
