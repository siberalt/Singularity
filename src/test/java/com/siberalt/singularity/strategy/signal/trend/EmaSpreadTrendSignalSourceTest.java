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

class EmaSpreadTrendSignalSourceTest {
    private final EmaSpreadTrendSignalSource calculator = new EmaSpreadTrendSignalSource(3, 8, 0.01, 0.0005);

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
    void should_ReturnNeutral_WhenSpreadIsInsideTheFlatZone() {
        // 0.001 a bar on 100 keeps the spread far below the threshold
        assertEquals(Signal.NEUTRAL, calculator.calculate(line(40, 100, 0.001)));
    }

    @Test
    void should_StayWithinMinusOneAndOne_WhenTrendIsVeryStrong() {
        Signal result = calculator.calculate(line(40, 100, 20));

        assertTrue(result.confidence() <= 1.0);
        assertTrue(result.confidence() > 0.99);
    }

    @Test
    void should_ReturnNeutral_WhenThereAreFewerCandlesThanTheSlowPeriod() {
        assertEquals(Signal.NEUTRAL, calculator.calculate(line(7, 100, 1)));
    }

    @Test
    void should_ReturnNeutral_WhenNullOrEmpty() {
        assertEquals(Signal.NEUTRAL, calculator.calculate(null));
        assertEquals(Signal.NEUTRAL, calculator.calculate(List.of()));
    }

    @Test
    void should_RefuseBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new EmaSpreadTrendSignalSource(8, 8));
        assertThrows(IllegalArgumentException.class, () -> new EmaSpreadTrendSignalSource(0, 8));
        assertThrows(IllegalArgumentException.class, () -> new EmaSpreadTrendSignalSource(3, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EmaSpreadTrendSignalSource(3, 8, 0.01, -1));
    }
}
