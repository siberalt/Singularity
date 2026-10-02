package com.siberalt.singularity.strategy.upside.trend;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmaSlopeTrendUpsideCalculatorTest {
    private final EmaSlopeTrendUpsideCalculator calculator = new EmaSlopeTrendUpsideCalculator(5, 5, 0.01, 0.0005);

    private List<Candle> line(int count, double start, double step) {
        List<Candle> candles = new ArrayList<>(count);

        for (int at = 0; at < count; at++) {
            double close = start + step * at;
            candles.add(Candle.of(TimePoint.NULL, 100, close, close, close, close));
        }

        return candles;
    }

    @Test
    void should_ReturnPositiveSignal_WhenPriceRises() {
        Upside result = calculator.calculate(line(40, 100, 0.5));

        assertTrue(result.signal() > 0);
        assertTrue(result.strength() > 0);
    }

    @Test
    void should_ReturnNegativeSignal_WhenPriceFalls() {
        Upside result = calculator.calculate(line(40, 100, -0.5));

        assertTrue(result.signal() < 0);
        assertTrue(result.strength() > 0);
    }

    @Test
    void should_ReturnNeutral_WhenPriceIsFlat() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(line(40, 100, 0)));
    }

    @Test
    void should_ReturnNeutral_WhenSlopeIsInsideTheFlatZone() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(line(40, 100, 0.001)));
    }

    @Test
    void should_StayWithinMinusOneAndOne_WhenTrendIsVeryStrong() {
        Upside result = calculator.calculate(line(40, 100, 20));

        assertTrue(result.signal() <= 1.0);
        assertTrue(result.signal() > 0.99);
    }

    @Test
    void should_ReturnNeutral_WhenThereAreFewerCandlesThanPeriodPlusLookback() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(line(9, 100, 1)));
    }

    @Test
    void should_ReturnNeutral_WhenNullOrEmpty() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(null));
        assertEquals(Upside.NEUTRAL, calculator.calculate(List.of()));
    }

    @Test
    void should_RefuseBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendUpsideCalculator(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendUpsideCalculator(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendUpsideCalculator(5, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendUpsideCalculator(5, 5, 0.01, -1));
    }
}
