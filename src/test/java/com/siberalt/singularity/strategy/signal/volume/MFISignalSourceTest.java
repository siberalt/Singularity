package com.siberalt.singularity.strategy.signal.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MFISignalSourceTest {
    @Test
    void calculateReturnsZeroSignalWhenInsufficientCandles() {
        MFISignalSource calculator = new MFISignalSource();
        List<Candle> candles = List.of(Candle.of(Instant.now(), 10, 15, 9, 14, 100));

        Signal result = calculator.calculate(candles);

        assertEquals(0, result.confidence());
        assertEquals(0, result.strength());
    }

    @Test
    void calculateReturnsMaxBullishSignalWhenNoNegativeFlow() {
        MFISignalSource calculator = new MFISignalSource();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150)
        );

        Signal result = calculator.calculate(candles);

        assertEquals(1, result.confidence());
        assertEquals(1, result.strength());
    }

    @Test
    void calculateReturnsNormalizedSignalForMixedFlows() {
        MFISignalSource calculator = new MFISignalSource();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150),
            Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 15, 17, 14, 13, 200)
        );

        Signal result = calculator.calculate(candles);

        assertTrue(result.confidence() >= -1 && result.confidence() <= 1);
        assertEquals(Math.abs(result.confidence()), result.strength());
    }

    @Test
    void calculateHandlesZeroPriceDifference() {
        MFISignalSource calculator = new MFISignalSource();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 10, 15, 9, 14, 100)
        );

        Signal result = calculator.calculate(candles);

        assertEquals(0, result.confidence());
        assertEquals(0, result.strength());
    }

    @Test
    void calculateHandlesLargeVolumeAndPriceChange() {
        MFISignalSource calculator = new MFISignalSource();
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 1_000_000),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 20, 2_000_000)
        );

        Signal result = calculator.calculate(candles);

        assertTrue(result.confidence() > 0);
        assertEquals(Math.abs(result.confidence()), result.strength());
    }
}
