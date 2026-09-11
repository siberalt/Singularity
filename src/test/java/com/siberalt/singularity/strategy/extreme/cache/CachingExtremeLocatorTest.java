package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CachingExtremeLocatorTest {
    private final CandleFactory candleFactory = new CandleFactory("instrument1");

    @Test
    void locateReturnsEmptyListWhenNoExtremesFound() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:07:00Z", 110)
        );

        ExtremeRange outerRange = createOuterRange(0, 10);

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of());
        when(rangeRepository.getNeighbors(outerRange, RangeType.OUTER)).thenReturn(List.of());
        when(baseLocator.locate(candles)).thenReturn(List.of());

        List<Candle> result = locator.locate(candles);

        assertEquals(List.of(), result);
        verify(extremeRepository, never()).saveBatch(any(), any());
        verify(rangeRepository, never()).saveBatch(any());
    }

    @Test
    void locateHandlesEmptyInputCandlesGracefully() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of();

        List<Candle> result = locator.locate(candles);

        assertEquals(List.of(), result);
        verifyNoInteractions(baseLocator, rangeRepository, extremeRepository);
    }

    @Test
    void locateCachesExtremesForFullRange() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 90),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        ExtremeRange outerRange = createOuterRange(0, 4);
        ExtremeRange innerRange = createInnerRange(2, 4);
        List<Candle> expectedExtremes = List.of(
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        when(rangeRepository.getIntersects(outerRange, RangeType.INNER)).thenReturn(List.of());
        when(baseLocator.locate(candles)).thenReturn(expectedExtremes);

        List<Candle> result = locator.locate(candles);

        assertEquals(expectedExtremes, result);
        verify(rangeRepository, times(1)).saveBatch(List.of(outerRange, innerRange));
        verify(extremeRepository, times(1)).saveBatch(any(), eq(expectedExtremes));
    }

    @Test
    void locateExtractsExtremesFullyFromCache() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 90),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        ExtremeRange cachedRange = createOuterRange(0, 10);
        ExtremeRange outerRange = createOuterRange(0, 4);
        List<Candle> cachedExtremes = List.of(
            candleFactory.createCommon("2024-01-01T00:01:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 90)
        );

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of(cachedRange));
        when(extremeRepository.getByRange(outerRange)).thenReturn(cachedExtremes);

        List<Candle> result = locator.locate(candles);

        assertEquals(cachedExtremes, result);
        verify(baseLocator, never()).locate(any());
        verify(rangeRepository, never()).saveBatch(any());
        verify(extremeRepository, never()).saveBatch(any(), any());
    }

    @Test
    void locateHandlesNewRangeWithNeighbor() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 90),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        ExtremeRange newOuterRange = createOuterRange(0, 4);
        ExtremeRange neighborOuterRange = createOuterRange(5, 9);
        ExtremeRange neighborInnerRange = createInnerRange(6, 7);
        ExtremeRange unitedOuterRange = createOuterRange(0, 9);
        ExtremeRange unitedInnerRange = createInnerRange(2, 7);

        List<Candle> newExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"),  120),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"),  130)
        );

        when(rangeRepository.getIntersects(newOuterRange, RangeType.OUTER)).thenReturn(List.of());
        when(rangeRepository.getNeighbors(newOuterRange, RangeType.OUTER)).thenReturn(List.of(neighborOuterRange));
        when(rangeRepository.getSubsets(unitedOuterRange, RangeType.INNER)).thenReturn(List.of(neighborInnerRange));

        when(extremeRepository.getByRange(newOuterRange)).thenReturn(newExtremes);
        when(extremeRepository.getInnerRange(unitedOuterRange)).thenReturn(unitedInnerRange);
        when(baseLocator.locate(candles)).thenReturn(newExtremes);

        List<Candle> result = locator.locate(candles);

        assertEquals(newExtremes, result);

        verify(extremeRepository, times(1)).saveBatch(newOuterRange, newExtremes);
        verify(rangeRepository, times(1)).saveBatch(List.of(unitedOuterRange, unitedInnerRange));
        verify(rangeRepository, times(1)).deleteBatch(anyList());
    }

    @Test
    void locateHandlesCachedExtremesAtRangeStart() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 130),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 90),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        ExtremeRange outerRange = createOuterRange(0, 4);
        ExtremeRange cachedOuterRange = createOuterRange(0, 3);
        ExtremeRange cachedInnerRange = createInnerRange(1, 2);
        ExtremeRange unitedInnerRange = createInnerRange(1, 4);
        ExtremeRange unitedOuterRange = createOuterRange(0, 4);
        ExtremeRange newUnitedOuterRange = createOuterRange(1, 4);
        ExtremeRange saveInnerRange = createInnerRange(3, 4);

        List<Candle> cachedExtremes = List.of(
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 130)
        );
        List<Candle> baseLocatorExtremes = List.of(
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of(cachedOuterRange));
        when(rangeRepository.getIntersects(outerRange, RangeType.INNER)).thenReturn(List.of(cachedInnerRange));
        when(rangeRepository.getSubsets(unitedOuterRange, RangeType.INNER)).thenReturn(List.of(cachedInnerRange));
        when(extremeRepository.getByRange(cachedInnerRange)).thenReturn(cachedExtremes);
        when(extremeRepository.getInnerRange(newUnitedOuterRange)).thenReturn(unitedInnerRange);
        when(baseLocator.locate(candles.subList(3, 5))).thenReturn(baseLocatorExtremes);

        List<Candle> result = locator.locate(candles);

        assertEquals(List.of(
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 130),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        ), result);

        verify(baseLocator, times(1)).locate(candles.subList(3, 5));
        verify(extremeRepository, times(1)).saveBatch(saveInnerRange, baseLocatorExtremes);
    }

    @Test
    void locateCachesEmptyListWhenNoExtremesFoundWithIntersection() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:07:00Z", 110)
        );

        ExtremeRange outerRange = createOuterRange(0, 7);

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of(createOuterRange(9, 12)));
        when(rangeRepository.getNeighbors(outerRange, RangeType.OUTER)).thenReturn(List.of());
        when(baseLocator.locate(candles)).thenReturn(List.of());

        List<Candle> result = locator.locate(candles);

        assertEquals(List.of(), result);
        verify(extremeRepository, never()).saveBatch(any(), any());
        verify(rangeRepository, atMostOnce()).saveBatch(any());
    }

    @Test
    void locateHandlesLeftIntersectAndRightNeighbor() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);
        List<Candle> leftIntersectCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120)
        );
        List<Candle> newCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 90),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 130)
        );
        List<Candle> rightNeighborCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:07:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:08:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:09:00Z", 120)
        );

        List<Candle> missingRangeCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:03:00Z", 100),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 90),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 130)
        );

        List<Candle> newExtremes = List.of(
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
        );

        ExtremeRange newOuterRange = createOuterRange(2, 6);
        ExtremeRange leftIntersectingOuterRange = createOuterRange(0, 2);
        ExtremeRange leftIntersectingInnerRange = createInnerRange(1, 2);
        ExtremeRange rightNeighborOuterRange = createOuterRange(7, 9);
        ExtremeRange rightNeighborInnerRange = createInnerRange(8, 8);
        ExtremeRange unitedOuterRange = createOuterRange(0, 9);
        ExtremeRange unitedInnerRange = createInnerRange(1, 8);
        ExtremeRange saveExtremesRange = createInnerRange(3, 6);

        when(rangeRepository.getIntersects(newOuterRange, RangeType.OUTER)).thenReturn(List.of(leftIntersectingOuterRange));
        when(rangeRepository.getIntersects(newOuterRange, RangeType.INNER)).thenReturn(List.of(leftIntersectingInnerRange));
        when(rangeRepository.getNeighbors(newOuterRange, RangeType.OUTER)).thenReturn(List.of(rightNeighborOuterRange));
        when(rangeRepository.getSubsets(unitedOuterRange, RangeType.INNER)).thenReturn(
            List.of(leftIntersectingInnerRange, rightNeighborInnerRange)
        );

        when(extremeRepository.getInnerRange(unitedOuterRange)).thenReturn(unitedInnerRange);
        when(baseLocator.locate(missingRangeCandles)).thenReturn(newExtremes);

        List<Candle> result = locator.locate(newCandles);

        assertEquals(newExtremes, result);

        verify(rangeRepository, times(1)).saveBatch(List.of(unitedOuterRange, unitedInnerRange));
        verify(extremeRepository, times(1)).saveBatch(saveExtremesRange, newExtremes);
        verify(rangeRepository, times(1)).deleteBatch(anyList());
    }

    /**
     * Candles rolled up to a wider interval keep the index of the raw bar they opened on, so an
     * hourly window carries indexes some sixty apart. The missing stretch used to be cut out by
     * counting off from the first index, which on such a window ran clean off the end of the list.
     */
    @Test
    void locatesTheMissingStretchOfCandlesWhoseIndexesAreFarApart() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = hourlyCandles(10);

        ExtremeRange outerRange = createOuterRange(0, 540);
        ExtremeRange cachedOuterRange = createOuterRange(0, 300);
        ExtremeRange cachedInnerRange = createInnerRange(0, 300);
        ExtremeRange missingInnerRange = createInnerRange(301, 540);

        List<Candle> cachedExtremes = List.of(candles.get(1));
        List<Candle> newExtremes = List.of(candles.get(7));

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of(cachedOuterRange));
        when(rangeRepository.getIntersects(outerRange, RangeType.INNER)).thenReturn(List.of(cachedInnerRange));
        when(rangeRepository.getNeighbors(outerRange, RangeType.OUTER)).thenReturn(List.of());
        when(rangeRepository.getSubsets(outerRange, RangeType.INNER)).thenReturn(List.of(cachedInnerRange));
        when(extremeRepository.getByRange(cachedInnerRange)).thenReturn(cachedExtremes);
        when(extremeRepository.getInnerRange(outerRange)).thenReturn(createInnerRange(60, 420));
        when(baseLocator.locate(candles.subList(6, 10))).thenReturn(newExtremes);

        List<Candle> result = locator.locate(candles);

        assertEquals(List.of(candles.get(1), candles.get(7)), result);
        verify(baseLocator, times(1)).locate(candles.subList(6, 10));
        verify(extremeRepository, times(1)).saveBatch(missingInnerRange, newExtremes);
    }

    private List<Candle> hourlyCandles(int amount) {
        return IntStream.range(0, amount)
            .mapToObj(position -> new Candle(
                "instrument1",
                new TimePoint(position * 60L),
                Quotation.of(100 + position),
                Quotation.of(100 + position),
                Quotation.of(100 + position),
                Quotation.of(100 + position),
                0
            ))
            .toList();
    }

    private ExtremeRange createOuterRange(int fromIndex, int toIndex) {
        return new ExtremeRange(fromIndex, toIndex, "instrument1", "DEFAULT", RangeType.OUTER);
    }

    private ExtremeRange createInnerRange(int fromIndex, int toIndex) {
        return new ExtremeRange(fromIndex, toIndex, "instrument1", "DEFAULT", RangeType.INNER);
    }
}
