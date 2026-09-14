package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            candleFactory.createCommon("2024-01-01T00:02:00Z", 120),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 130)
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

        // The extreme at 2 comes out of the cache, from the stretch the window overlaps on its left;
        // only the one at 4 lies in the stretch the base locator is asked about.
        List<Candle> cachedExtremes = newExtremes.subList(0, 1);
        List<Candle> locatedExtremes = newExtremes.subList(1, 2);

        when(extremeRepository.getInnerRange(unitedOuterRange)).thenReturn(unitedInnerRange);
        when(extremeRepository.getByRange(createInnerRange(2, 2))).thenReturn(cachedExtremes);
        when(baseLocator.locate(missingRangeCandles)).thenReturn(locatedExtremes);

        List<Candle> result = locator.locate(newCandles);

        assertEquals(newExtremes, result);

        verify(rangeRepository, times(1)).saveBatch(List.of(unitedOuterRange, unitedInnerRange));
        verify(extremeRepository, times(1)).saveBatch(saveExtremesRange, locatedExtremes);
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

    /**
     * The cache on its real repositories, walked the way every caller walks it: a window sliding
     * forward a bar at a time.
     * <p>
     * The tests above mock the repositories, and that is how a broken one went unseen - the mock
     * handed back a proper INNER range where the real repository handed back an OUTER one, and the
     * cache rescanned every window whole. Its answers stayed right, so nothing checking answers
     * alone could tell; only its cost gave it away. So these check both.
     */
    @Nested
    class SlidingWindow {
        private static final int WINDOW = 200;
        private static final int SLIDES = 50;
        private static final int THREADS = 4;

        /**
         * How far into a window the cache may know more than a plain scan: the pivot's vicinity,
         * plus the reach of the grouping that follows it.
         */
        private static final int OPENING_BARS = PivotPointExtremeLocator.DEFAULT_LEFT_VICINITY
            + PivotPointExtremeLocator.DEFAULT_EXTREME_AREA;

        private final List<Candle> wave = IntStream.range(0, 4 * WINDOW + SLIDES)
            .mapToObj(this::waveCandle)
            .toList();

        /**
         * The two agree on every window except in its opening bars, and there for a reason. A plain
         * scan cannot judge the first few bars of a window, having nothing to their left; the cache
         * judged them earlier, when they sat further in, and remembers. Nothing it returns comes
         * from past the window's last bar, so this is memory rather than look-ahead.
         */
        @Test
        void agreesWithAPlainScanPastTheWindowsOpeningBars() {
            PivotPointExtremeLocator pivot = PivotPointExtremeLocator.ofMinimums(5);
            CachingExtremeLocator cached = new CachingExtremeLocator(PivotPointExtremeLocator.ofMinimums(5));

            for (int bar = WINDOW; bar < WINDOW + SLIDES; bar++) {
                List<Candle> window = wave.subList(bar - WINDOW, bar);
                List<Candle> expected = pivot.locate(window);
                List<Candle> actual = cached.locate(window);
                long openingEnd = window.get(OPENING_BARS).getIndex();

                for (Candle extreme : expected) {
                    assertTrue(actual.contains(extreme) || extreme.getIndex() < openingEnd,
                        "window ending at " + bar + " lost " + extreme.getIndex());
                }

                for (Candle extreme : actual) {
                    assertTrue(expected.contains(extreme) || extreme.getIndex() < openingEnd,
                        "window ending at " + bar + " added " + extreme.getIndex());
                }
            }
        }

        /**
         * What a slide costs has to depend on what it adds, not on how wide the window is - that is
         * the whole point of the cache. So the same bars are slid over with a narrow window and with
         * one four times wider, and the wider may not cost meaningfully more.
         */
        @Test
        void scansWhatEachSlideAddsWhateverTheWindow() {
            long narrow = scannedOverSlides(WINDOW);
            long wide = scannedOverSlides(4 * WINDOW);

            assertTrue(wide < 1.5 * narrow, "narrow window scanned " + narrow + " bars, four times wider " + wide);
            assertTrue(narrow < (SLIDES - 1L) * WINDOW, "narrow window scanned " + narrow + " bars, as much as rescanning");
        }

        /**
         * Grouping laid over the cache, as it has to be: the pivots are cached, the grouping sees
         * each window whole. Together they answer as the grouping locator does on its own - bar the
         * opening bars, as above, and the one run those can reach into: a pivot remembered there can
         * join the window's first run and win it, which costs the plain scan's winner of that run.
         * <p>
         * On a series whose dips come in pairs, so pivots do fall close enough together to be grouped.
         */
        @Test
        void groupsOverTheCacheAsAPlainGroupingScanWould() {
            PivotPointExtremeLocator pivot = PivotPointExtremeLocator.ofMinimums(5);
            ExtremeLocator cached = pivot.groupingOf(new CachingExtremeLocator(pivot.withoutGrouping()));
            List<Candle> paired = IntStream.range(0, WINDOW + SLIDES).mapToObj(this::pairedDipCandle).toList();
            long groupedAway = 0;

            for (int bar = WINDOW; bar < WINDOW + SLIDES; bar++) {
                List<Candle> window = paired.subList(bar - WINDOW, bar);
                List<Candle> expected = pivot.locate(window);
                List<Candle> actual = cached.locate(window);
                long openingEnd = window.get(OPENING_BARS).getIndex();

                groupedAway += pivot.withoutGrouping().locate(window).size() - expected.size();

                for (Candle extreme : expected.subList(Math.min(1, expected.size()), expected.size())) {
                    assertTrue(actual.contains(extreme), "window ending at " + bar + " lost " + extreme.getIndex());
                }

                for (Candle extreme : actual) {
                    assertTrue(expected.contains(extreme) || extreme.getIndex() < openingEnd,
                        "window ending at " + bar + " added " + extreme.getIndex());
                }
            }

            assertTrue(groupedAway > 0, "no pivots fell close enough together to test the grouping");
        }

        /**
         * Every thirty bars, a dip to 90 and seven bars later one to 91. Seven is past the pivot's
         * vicinity of five, so both are pivots, and within the grouping's reach of ten, so they are
         * one run - whose winner is the deeper, first dip.
         */
        private Candle pairedDipCandle(int index) {
            int offset = index % 30;
            double depth = Math.max(0, 10 - 2 * Math.abs(offset - 10)) + Math.max(0, 9 - 2 * Math.abs(offset - 17));
            Quotation price = Quotation.of(100 - depth);

            return new Candle("instrument1", new TimePoint(index, Instant.EPOCH.plusSeconds(60L * index)),
                price, price, price, price, 0);
        }

        /**
         * One cache, several threads over the same windows - the candidates of one fold, which differ
         * in what they make of the extremes rather than in which bars they ask about.
         */
        @Test
        void answersThreadsSharingItAsAPlainScanWould() throws Exception {
            PivotPointExtremeLocator pivot = PivotPointExtremeLocator.ofMinimums(5);
            CachingExtremeLocator cache = new CachingExtremeLocator(pivot.withoutGrouping())
                .setMultithreaded(true);
            // The margin between cache and grouping is what keeps a thread still short of some bar
            // from being handed an extreme that only the bars beyond it could settle.
            ExtremeLocator shared = pivot.groupingOf(pivot.confirmedOf(cache));

            walkInParallel(THREADS, () -> {
                for (int bar = WINDOW; bar < WINDOW + SLIDES; bar++) {
                    List<Candle> window = wave.subList(bar - WINDOW, bar);
                    List<Candle> expected = pivot.locate(window);
                    List<Candle> actual = shared.locate(window);
                    long openingEnd = window.get(OPENING_BARS).getIndex();

                    for (Candle extreme : expected) {
                        assertTrue(actual.contains(extreme) || extreme.getIndex() < openingEnd,
                            "window ending at " + bar + " lost " + extreme.getIndex());
                    }

                    for (Candle extreme : actual) {
                        assertTrue(expected.contains(extreme) || extreme.getIndex() < openingEnd,
                            "window ending at " + bar + " added " + extreme.getIndex());
                    }
                }
            });
        }

        /**
         * The whole reason to share one: the threads between them scan less than they would each
         * holding their own, because what one has scanned the rest find waiting.
         */
        @Test
        void scansLessThanTheThreadsWouldOnTheirOwn() throws Exception {
            long alone = scannedWalkingWith(1);
            long together = scannedWalkingWith(THREADS);

            assertTrue(together < THREADS * alone,
                THREADS + " threads scanned " + together + " bars, as much as " + alone + " each");
        }

        /** Bars handed to the base locator while one cache is walked by this many threads. */
        private long scannedWalkingWith(int threads) throws Exception {
            PivotPointExtremeLocator pivot = PivotPointExtremeLocator.ofMinimums(5);
            AtomicLong scanned = new AtomicLong();
            ExtremeLocator shared = new CachingExtremeLocator(stretch -> {
                scanned.addAndGet(stretch.size());

                return pivot.withoutGrouping().locate(stretch);
            }).setMultithreaded(true);

            walkInParallel(threads, () -> {
                for (int bar = WINDOW; bar < WINDOW + SLIDES; bar++) {
                    shared.locate(wave.subList(bar - WINDOW, bar));
                }
            });

            return scanned.get();
        }

        private void walkInParallel(int threads, Runnable walk) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(threads);

            try {
                List<Callable<Void>> walkers = new ArrayList<>();

                for (int thread = 0; thread < threads; thread++) {
                    walkers.add(() -> {
                        walk.run();

                        return null;
                    });
                }

                for (Future<Void> result : pool.invokeAll(walkers)) {
                    result.get();
                }
            } finally {
                pool.shutdownNow();
            }
        }

        /** Bars handed to the base locator while a window of this width slides over the last bars. */
        private long scannedOverSlides(int window) {
            PivotPointExtremeLocator pivot = PivotPointExtremeLocator.ofMinimums(5);
            long[] scanned = {0};
            CachingExtremeLocator cached = new CachingExtremeLocator(stretch -> {
                scanned[0] += stretch.size();

                return pivot.locate(stretch);
            });

            // Both widths end on the same bars, so what is new on each slide is the same for both.
            int end = wave.size();

            cached.locate(wave.subList(end - SLIDES - window, end - SLIDES));
            scanned[0] = 0;

            for (int bar = end - SLIDES + 1; bar <= end; bar++) {
                cached.locate(wave.subList(bar - window, bar));
            }

            return scanned[0];
        }

        /** A wave, so a window of it holds a handful of minimums rather than none or one per bar. */
        private Candle waveCandle(int index) {
            Quotation price = Quotation.of(100 + 10 * Math.sin(index / 7.0));

            return new Candle("instrument1", new TimePoint(index, Instant.EPOCH.plusSeconds(60L * index)),
                price, price, price, price, 0);
        }
    }
}
