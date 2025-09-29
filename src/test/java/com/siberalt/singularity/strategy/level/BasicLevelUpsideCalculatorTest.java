package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.level.BasicLevelBasedUpsideCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicLevelUpsideCalculatorTest {

    @Test
    void calculatesUpsideWhenResistanceIsBreached() {
        Level<Double> resistance = mock(Level.class);
        Level<Double> support = mock(Level.class);

        when(resistance.getFunction()).thenReturn(x -> 100.0);
        when(resistance.getStrength()).thenReturn(2.0);
        when(support.getFunction()).thenReturn(x -> 75.0);
        when(support.getStrength()).thenReturn(1.0);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(110.0, resistance, support);

        assertEquals(1, result.signal(), 0.01);
        assertTrue(result.strength() > 1);
    }

    @Test
    void calculatesUpsideWhenSupportIsBreached() {
        Level<Double> resistance = mock(Level.class);
        Level<Double> support = mock(Level.class);

        when(resistance.getFunction()).thenReturn(x -> 100.0);
        when(resistance.getStrength()).thenReturn(2.0);
        when(support.getFunction()).thenReturn(x -> 75.0);
        when(support.getStrength()).thenReturn(1.0);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(70.0, resistance, support);

        assertEquals(-1, result.signal(), 0.01);
        assertTrue(result.strength() < -1);
    }

    @Test
    void returnsZeroUpsideWhenResistancePriceIsLessThanOrEqualToSupportPrice() {
        Level<Double> resistance = mock(Level.class);
        Level<Double> support = mock(Level.class);

        when(resistance.getFunction()).thenReturn(x -> 100.0);
        when(support.getFunction()).thenReturn(x -> 100.0);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(110.0, resistance, support);

        assertNotNull(result);
        assertEquals(0.0, result.signal(), 1e-9);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsInsideCanal() {
        Level<Double> resistance = createLevelMock(10.0, 5.0);
        Level<Double> support = createLevelMock(5.0, 5.0);
        double currentPrice = 8.0;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(currentPrice, resistance, support);

        assertNotNull(result);
        assertEquals(result.signal(), -0.2, 1e-9);
        assertEquals(result.strength(), -0.2, 1e-9);
    }

    private Level<Double> createLevelMock(double price, double strength) {
        Level<Double> level = mock(Level.class);
        when(level.getFunction()).thenReturn(x -> price);
        when(level.getStrength()).thenReturn(strength);
        return level;
    }
}
