package com.siberalt.singularity.strategy.signal.trend;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmaSlopeTrendSignalSourceTest {
    private final EmaSlopeTrendSignalSource calculator = new EmaSlopeTrendSignalSource(5, 5, 0.01, 0.0005);

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
        Signal result = calculator.calculate(line(40, 100, 0.5));

        assertTrue(result.confidence() > 0);
        assertTrue(result.strength() > 0);
    }

    @Test
    void should_ReturnNegativeSignal_WhenPriceFalls() {
        Signal result = calculator.calculate(line(40, 100, -0.5));

        assertTrue(result.confidence() < 0);
        assertTrue(result.strength() > 0);
    }

    @Test
    void should_ReturnNeutral_WhenPriceIsFlat() {
        assertEquals(Signal.NEUTRAL, calculator.calculate(line(40, 100, 0)));
    }

    @Test
    void should_ReturnNeutral_WhenSlopeIsInsideTheFlatZone() {
        assertEquals(Signal.NEUTRAL, calculator.calculate(line(40, 100, 0.001)));
    }

    @Test
    void should_StayWithinMinusOneAndOne_WhenTrendIsVeryStrong() {
        Signal result = calculator.calculate(line(40, 100, 20));

        assertTrue(result.confidence() <= 1.0);
        assertTrue(result.confidence() > 0.99);
    }

    @Test
    void should_ReturnNeutral_WhenThereAreFewerCandlesThanPeriodPlusLookback() {
        assertEquals(Signal.NEUTRAL, calculator.calculate(line(9, 100, 1)));
    }

    @Test
    void should_ReturnNeutral_WhenNullOrEmpty() {
        assertEquals(Signal.NEUTRAL, calculator.calculate(null));
        assertEquals(Signal.NEUTRAL, calculator.calculate(List.of()));
    }

    @Test
    void should_RefuseBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendSignalSource(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendSignalSource(5, 0));
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendSignalSource(5, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EmaSlopeTrendSignalSource(5, 5, 0.01, -1));
    }
}
