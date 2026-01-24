package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaseExtremesLocatorTest {

    @Test
    void locateReturnsEmptyListWhenCandlesIsEmpty() {
        BaseExtremeLocator locator = BaseExtremeLocator.createMaxLocator(x -> x.getClosePrice().toDouble());

        List<Candle> result = locator.locate(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void locateFindsMaximumCorrectly() {
        BaseExtremeLocator locator = BaseExtremeLocator.createMaxLocator(x -> x.getClosePrice().toDouble());
        Candle extremeCandle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 20);

        List<Candle> result = locator.locate(
            List.of(
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 12),
                extremeCandle,
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 18)
            )
        );

        assertEquals(1, result.size());
        assertSame(extremeCandle, result.get(0));
    }

    @Test
    void locateFindsMinimumCorrectly() {
        BaseExtremeLocator locator = BaseExtremeLocator.createMinLocator(x -> x.getClosePrice().toDouble());
        Candle extremeCandle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 12);

        List<Candle> result = locator.locate(
            List.of(
                extremeCandle,
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 20),
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 18)
            )
        );

        assertEquals(1, result.size());
        assertSame(extremeCandle, result.get(0));
    }

    @Test
    void locateHandlesSingleCandleCorrectly() {
        Candle candle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 10);
        BaseExtremeLocator locator = BaseExtremeLocator.createMaxLocator(x -> x.getClosePrice().toDouble());

        List<Candle> result = locator.locate(List.of(candle));

        assertEquals(1, result.size());
        assertSame(candle, result.get(0));
    }
}
