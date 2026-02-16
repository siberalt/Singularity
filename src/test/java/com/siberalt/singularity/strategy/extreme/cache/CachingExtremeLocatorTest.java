package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
    void locateHandlesRangeExceedingMaxLength() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ExtremeRangeRepository rangeRepository = mock(ExtremeRangeRepository.class);
        ExtremeRepository extremeRepository = mock(ExtremeRepository.class);

        CachingExtremeLocator locator = new CachingExtremeLocator(
            baseLocator, rangeRepository, extremeRepository, "DEFAULT", 8
        );

        List<Candle> baseLocatorCandles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:07:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:08:00Z", 110)
        );
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:07:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:08:00Z", 110),
            candleFactory.createCommon("2024-01-01T00:09:00Z", 110)
        );

        ExtremeRange newOuterRange = createOuterRange(0, 9);
        ExtremeRange intersectedOuterRange = createOuterRange(4, 12);
        ExtremeRange intersectedInnerRange = createInnerRange(9, 11);
        ExtremeRange trimmedInnerRange = createInnerRange(2, 4);
        ExtremeRange trimmedOuterRange = createOuterRange(0, 7);

        when(rangeRepository.getIntersects(newOuterRange, RangeType.OUTER)).thenReturn(List.of(intersectedOuterRange));
        when(rangeRepository.getIntersects(newOuterRange, RangeType.INNER)).thenReturn(List.of(intersectedInnerRange));
        when(rangeRepository.getSubsets(trimmedOuterRange, RangeType.INNER)).thenReturn(List.of(intersectedInnerRange));
        when(extremeRepository.getInnerRange(trimmedOuterRange)).thenReturn(trimmedInnerRange);

        List<Candle> baseLocatorExtremes = List.of(
            Candle.of(Instant.parse("2024-01-01T00:02:00Z"), "instrument1", 100, 110),
            Candle.of(Instant.parse("2024-01-01T00:04:00Z"), "instrument1", 100, 110)
        );
        when(baseLocator.locate(baseLocatorCandles)).thenReturn(baseLocatorExtremes);
        locator.locate(candles);

        verify(extremeRepository, times(1)).deleteBatch(anyList());
        verify(rangeRepository, times(1)).saveBatch(List.of(trimmedOuterRange, trimmedInnerRange));
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

    private ExtremeRange createOuterRange(int fromIndex, int toIndex) {
        return new ExtremeRange(fromIndex, toIndex, "instrument1", "DEFAULT", RangeType.OUTER);
    }

    private ExtremeRange createInnerRange(int fromIndex, int toIndex) {
        return new ExtremeRange(fromIndex, toIndex, "instrument1", "DEFAULT", RangeType.INNER);
    }
}
