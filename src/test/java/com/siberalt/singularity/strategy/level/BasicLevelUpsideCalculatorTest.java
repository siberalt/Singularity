package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.level.BasicLevelBasedUpsideCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicLevelUpsideCalculatorTest {
    private final CandleIndexProvider candleIndexProvider = mock(CandleIndexProvider.class);

    @Test
    void calculatesUpsideWhenResistanceIsBreached() {
        Level<Double> resistance = new Level<>(null, null, 0L, 0L, x -> 100.0, 2.0);
        Level<Double> support = new Level<>(null, null, 0L, 0L, x -> 75.0, 1.0);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(resistance, support, List.of(createCandle(110)), candleIndexProvider);

        assertEquals(1, result.signal(), 0.01);
        assertTrue(result.strength() > 1);
    }

    @Test
    void calculatesUpsideWhenSupportIsBreached() {
        Level<Double> resistance = new Level<>(null, null, 0L, 0L, x -> 100.0, 2.0);
        Level<Double> support = new Level<>(null, null, 0L, 0L, x -> 75.0, 1.0);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(
            resistance,
            support,
            List.of(createCandle(70.0)),
            candleIndexProvider
        );

        assertEquals(-1, result.signal(), 0.01);
        assertTrue(result.strength() <= -1);
    }

    @Test
    void returnsZeroUpsideWhenResistancePriceIsLessThanOrEqualToSupportPrice() {
        Level<Double> resistance = new Level<>(null, null, 0L, 0L, x -> 100.0, 0.0);
        Level<Double> support = new Level<>(null, null, 0L, 0L, x -> 100.0, 0.0);

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();

        Upside result = calculator.calculate(
            resistance,
            support,
            List.of(createCandle(110.0)),
            candleIndexProvider
        );

        assertNotNull(result);
        assertEquals(0.0, result.signal(), 1e-9);
    }

    @Test
    void calculatesUpsideWhenCurrentPriceIsInsideCanal() {
        Level<Double> resistance = createLevel(10.0, 5.0);
        Level<Double> support = createLevel(5.0, 5.0);
        double currentPrice = 8.0;

        BasicLevelBasedUpsideCalculator calculator = new BasicLevelBasedUpsideCalculator();
        Upside result = calculator.calculate(
            resistance,
            support,
            List.of(createCandle(currentPrice)),
            candleIndexProvider
        );

        assertNotNull(result);
        assertEquals(result.signal(), -0.2, 1e-9);
        assertEquals(result.strength(), -0.2, 1e-9);
    }

    private Candle createCandle(double price) {
        Candle candle = Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, price);
        when(candleIndexProvider.provideIndex(candle)).thenReturn(0L);

        return candle;
    }

    private Level<Double> createLevel(double price, double strength) {
        return new Level<>(null, null, 0L, 0L, x -> price, strength);
    }
}
