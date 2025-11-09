package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BaseExtremumLocatorTest {

    @Test
    void locateReturnsEmptyListWhenCandlesIsEmpty() {
        BaseExtremumLocator locator = BaseExtremumLocator.createMaxLocator(x -> x.getClosePrice().toDouble());

        List<Candle> result = locator.locate(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void locateFindsMaximumCorrectly() {
        BaseExtremumLocator locator = BaseExtremumLocator.createMaxLocator(x -> x.getClosePrice().toDouble());
        Candle extremumCandle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 20);

        List<Candle> result = locator.locate(
            List.of(
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 12),
                extremumCandle,
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 18)
            )
        );

        assertEquals(1, result.size());
        assertSame(extremumCandle, result.get(0));
    }

    @Test
    void locateFindsMinimumCorrectly() {
        BaseExtremumLocator locator = BaseExtremumLocator.createMinLocator(x -> x.getClosePrice().toDouble());
        Candle extremumCandle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 12);

        List<Candle> result = locator.locate(
            List.of(
                extremumCandle,
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 20),
                Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 18)
            )
        );

        assertEquals(1, result.size());
        assertSame(extremumCandle, result.get(0));
    }

    @Test
    void locateHandlesSingleCandleCorrectly() {
        Candle candle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 10);
        BaseExtremumLocator locator = BaseExtremumLocator.createMaxLocator(x -> x.getClosePrice().toDouble());

        List<Candle> result = locator.locate(List.of(candle));

        assertEquals(1, result.size());
        assertSame(candle, result.get(0));
    }
}
