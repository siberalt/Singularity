package com.siberalt.singularity.strategy.extremum.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extremum.ExtremumLocator;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.market.DefaultCandleIndexProvider;
import com.siberalt.singularity.utils.ListUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CachingExtremeLocatorTest {
    DefaultCandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();

    @Test
    void locateReturnsEmptyListWhenNoExtremesFound() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110, 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:05:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:06:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:07:00Z"), "instrument1", 100, 110)
        );
        candleIndexProvider.accumulate(candles);
        Range outerRange = new Range(0, 10, "instrument1", "DEFAULT");

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of());
        when(rangeRepository.getNeighbors(outerRange, RangeType.OUTER)).thenReturn(List.of());
        when(baseLocator.locate(candles, candleIndexProvider)).thenReturn(List.of());

        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(List.of(), result);
        verify(extremeRepository, never()).saveBatch(any(), any(), any());
        verify(rangeRepository, never()).saveBatch(any());
    }

    @Test
    void locateHandlesEmptyInputCandlesGracefully() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of();

        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(List.of(), result);
        verifyNoInteractions(baseLocator, rangeRepository, extremeRepository);
    }

    @Test
    void locateThrowsExceptionWhenCandleIndexProviderReturnsInvalidIndexes() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);
        CandleIndexProvider candleIndexProvider = mock(CandleIndexProvider.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110, 32),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100, 34)
        );

        when(candleIndexProvider.provideIndex(any())).thenReturn(-1L);

        assertThrows(IllegalArgumentException.class, () -> locator.locate(candles, candleIndexProvider));
    }

    @Test
    void locateCachesExtremesForFullRange() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 90),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );
        candleIndexProvider.accumulate(candles);

        Range outerRange = new Range(
            0, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range innerRange = new Range(
            2, 4, "instrument1", "DEFAULT", RangeType.INNER
        );
        List<Candle> expectedExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );

        when(rangeRepository.getIntersects(outerRange, RangeType.INNER)).thenReturn(List.of());
        when(baseLocator.locate(candles, candleIndexProvider)).thenReturn(expectedExtremes);

        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(expectedExtremes, result);
        verify(rangeRepository, times(1)).saveBatch(List.of(outerRange, innerRange));
        verify(extremeRepository, times(1)).saveBatch(any(), eq(expectedExtremes), eq(candleIndexProvider));
    }

    @Test
    void locateExtractsExtremesFullyFromCache() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 90),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );

        Range cachedRange = new Range(
            0, 10, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range outerRange = new Range(
            0, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );
        List<Candle> cachedExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 130)
        );

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of(cachedRange));
        when(extremeRepository.getByRange(outerRange)).thenReturn(cachedExtremes);

        candleIndexProvider.accumulate(candles);
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(cachedExtremes, result);
        verify(baseLocator, never()).locate(any(), any());
        verify(rangeRepository, never()).saveBatch(any());
        verify(extremeRepository, never()).saveBatch(any(), any(), any());
    }

    @Test
    void locateHandlesNewRangeWithNeighbor() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 90),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );

        Range newOuterRange = new Range(
            0, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range neighborOuterRange = new Range(
            5, 9, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range neighborInnerRange = new Range(
            6, 7, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range unitedOuterRange = new Range(
            0, 9, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range unitedInnerRange = new Range(
            2, 7, "instrument1", "DEFAULT", RangeType.INNER
        );

        List<Candle> newExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );

        when(rangeRepository.getIntersects(newOuterRange, RangeType.OUTER)).thenReturn(List.of());
        when(rangeRepository.getNeighbors(newOuterRange, RangeType.OUTER)).thenReturn(List.of(neighborOuterRange));
        when(rangeRepository.getSubsets(unitedOuterRange, RangeType.INNER)).thenReturn(List.of(neighborInnerRange));

        when(extremeRepository.getByRange(newOuterRange)).thenReturn(newExtremes);
        when(extremeRepository.getInnerRange(unitedOuterRange)).thenReturn(unitedInnerRange);
        when(baseLocator.locate(candles, candleIndexProvider)).thenReturn(newExtremes);

        candleIndexProvider.accumulate(candles);

        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(newExtremes, result);

        verify(extremeRepository, times(1)).saveBatch(newOuterRange, newExtremes, candleIndexProvider);
        verify(rangeRepository, times(1)).saveBatch(List.of(unitedOuterRange, unitedInnerRange));
        verify(rangeRepository, times(1)).deleteBatch(anyList());
    }

    @Test
    void locateHandlesCachedExtremesAtRangeStart() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 130),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 90),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );

        Range outerRange = new Range(
            0, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range cachedOuterRange = new Range(
            0, 3, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range cachedInnerRange = new Range(
            1, 2, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range unitedInnerRange = new Range(
            1, 4, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range unitedOuterRange = new Range(
            0, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range newUnitedOuterRange = new Range(
            1, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range saveInnerRange = new Range(
            3, 4, "instrument1", "DEFAULT", RangeType.OUTER
        );

        List<Candle> cachedExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120)
        );
        List<Candle> baseLocatorExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 150)
        );

        when(rangeRepository.getIntersects(outerRange, RangeType.OUTER)).thenReturn(List.of(cachedOuterRange));
        when(rangeRepository.getIntersects(outerRange, RangeType.INNER)).thenReturn(List.of(cachedInnerRange));
        when(rangeRepository.getSubsets(unitedOuterRange, RangeType.INNER)).thenReturn(List.of(cachedInnerRange));
        when(extremeRepository.getByRange(cachedInnerRange)).thenReturn(cachedExtremes);
        when(extremeRepository.getInnerRange(newUnitedOuterRange)).thenReturn(unitedInnerRange);
        when(baseLocator.locate(candles.subList(3, 5), candleIndexProvider)).thenReturn(baseLocatorExtremes);

        candleIndexProvider.accumulate(candles);
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(List.of(
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 150)
        ), result);

        verify(baseLocator, times(1)).locate(candles.subList(3, 5), candleIndexProvider);
        verify(extremeRepository, times(1)).saveBatch(
            eq(saveInnerRange), eq(baseLocatorExtremes), eq(candleIndexProvider)
        );
    }

    @Test
    void locateHandlesRangeExceedingMaxLength() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(
            baseLocator, rangeRepository, extremeRepository, "DEFAULT", 8
        );

        List<Candle> baseLocatorCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:05:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:06:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:07:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:08:00Z"), "instrument1", 110)
        );
        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:05:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:06:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:07:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:08:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:09:00Z"), "instrument1", 110)
        );

        Range newOuterRange = new Range(
            0, 9, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range intersectedOuterRange = new Range(
            4, 12, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range intersectedInnerRange = new Range(
            9, 11, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range trimmedInnerRange = new Range(
            2, 4, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range trimmedOuterRange = new Range(
            0, 7, "instrument1", "DEFAULT", RangeType.OUTER
        );

        when(rangeRepository.getIntersects(newOuterRange, RangeType.OUTER)).thenReturn(List.of(intersectedOuterRange));
        when(rangeRepository.getIntersects(newOuterRange, RangeType.INNER)).thenReturn(List.of(intersectedInnerRange));
        when(rangeRepository.getSubsets(trimmedOuterRange, RangeType.INNER)).thenReturn(List.of(intersectedInnerRange));
        when(extremeRepository.getInnerRange(trimmedOuterRange)).thenReturn(trimmedInnerRange);

        candleIndexProvider.accumulate(candles);

        List<Candle> baseLocatorExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 100, 110)
        );
        when(baseLocator.locate(baseLocatorCandles, candleIndexProvider)).thenReturn(baseLocatorExtremes);
        locator.locate(candles, candleIndexProvider);

        verify(extremeRepository, times(1)).deleteBatch(anyList());
        verify(rangeRepository, times(1)).saveBatch(List.of(trimmedOuterRange, trimmedInnerRange));
    }

    @Test
    void locateHandlesLeftIntersectAndRightNeighbor() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        RangeRepository rangeRepository = mock(RangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(baseLocator, rangeRepository, extremeRepository);

        List<Candle> leftIntersectCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:00:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:01:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120)
        );
        List<Candle> newCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:05:00Z"), "instrument1", 90),
            Candle.of(Instant.parse("2024-01-01T00:06:00Z"), "instrument1", 130)
        );
        List<Candle> rightNeighborCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:07:00Z"), "instrument1", 110),
            Candle.of(Instant.parse("2024-01-01T00:08:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:09:00Z"), "instrument1", 120)
        );

        List<Candle> missingRangeCandles = List.of(
            Candle.of(Instant.parse("2024-01-01T00:03:00Z"), "instrument1", 100),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:05:00Z"), "instrument1", 90),
            Candle.of(Instant.parse("2024-01-01T00:06:00Z"), "instrument1", 130)
        );

        List<Candle> newExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 120),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 130)
        );

        Range newOuterRange = new Range(
            2, 6, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range leftIntersectingOuterRange = new Range(
            0, 2, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range leftIntersectingInnerRange = new Range(
            1, 2, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range rightNeighborOuterRange = new Range(
            7, 9, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range rightNeighborInnerRange = new Range(
            8, 8, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range unitedOuterRange = new Range(
            0, 9, "instrument1", "DEFAULT", RangeType.OUTER
        );
        Range unitedInnerRange = new Range(
            1, 8, "instrument1", "DEFAULT", RangeType.INNER
        );
        Range saveExtremesRange = new Range(
            3, 6, "instrument1", "DEFAULT", RangeType.OUTER
        );

        when(rangeRepository.getIntersects(newOuterRange, RangeType.OUTER)).thenReturn(List.of(leftIntersectingOuterRange));
        when(rangeRepository.getIntersects(newOuterRange, RangeType.INNER)).thenReturn(List.of(leftIntersectingInnerRange));
        when(rangeRepository.getNeighbors(newOuterRange, RangeType.OUTER)).thenReturn(List.of(rightNeighborOuterRange));
        when(rangeRepository.getSubsets(unitedOuterRange, RangeType.INNER)).thenReturn(
            List.of(leftIntersectingInnerRange, rightNeighborInnerRange)
        );

        when(extremeRepository.getInnerRange(unitedOuterRange)).thenReturn(unitedInnerRange);
        when(baseLocator.locate(missingRangeCandles, candleIndexProvider)).thenReturn(newExtremes);

        candleIndexProvider.accumulate(ListUtils.merge(leftIntersectCandles, newCandles, rightNeighborCandles));

        List<Candle> result = locator.locate(newCandles, candleIndexProvider);

        assertEquals(newExtremes, result);

        verify(rangeRepository, times(1)).saveBatch(List.of(unitedOuterRange, unitedInnerRange));
        verify(extremeRepository, times(1)).saveBatch(saveExtremesRange, newExtremes, candleIndexProvider);
        verify(rangeRepository, times(1)).deleteBatch(anyList());
    }
}
