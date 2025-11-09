package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VPTUpsideCalculatorTest {
    @Test
    void calculateReturnsZeroUpsideWhenInsufficientData() {
        VPTUpsideCalculator calculator = new VPTUpsideCalculator();
        List<Candle> candles = List.of(Candle.of(Instant.now(), 10, 15, 9, 14, 100));

        Upside result = calculator.calculate(candles);

        assertEquals(0, result.signal());
        assertEquals(0, result.strength());
    }

    @Test
    void calculateReturnsNormalizedUpsideForValidData() {
        VPTUpsideCalculator calculator = new VPTUpsideCalculator();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150)
        );

        Upside result = calculator.calculate(candles);

        assertTrue(result.signal() >= -1 && result.signal() <= 1);
        assertEquals(Math.abs(result.signal()), result.strength());
    }

    @Test
    void calculateHandlesNegativePriceChange() {
        VPTUpsideCalculator calculator = new VPTUpsideCalculator();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 13, 150),
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100)
        );

        Upside result = calculator.calculate(candles);

        assertTrue(result.signal() < 0);
        assertEquals(Math.abs(result.signal()), result.strength());
    }

    @Test
    void calculateHandlesZeroPriceChange() {
        VPTUpsideCalculator calculator = new VPTUpsideCalculator();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 14, 150)
        );

        Upside result = calculator.calculate(candles);

        assertEquals(0, result.signal(), 0.0001);
        assertEquals(0, result.strength(), 0.0001);
    }

    @Test
    void calculateHandlesLargeVolumeAndPriceChange() {
        VPTUpsideCalculator calculator = new VPTUpsideCalculator();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 1_000_000),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 20, 2_000_000)
        );

        Upside result = calculator.calculate(candles);

        assertTrue(result.signal() > 0);
        assertEquals(Math.abs(result.signal()), result.strength());
    }
}
