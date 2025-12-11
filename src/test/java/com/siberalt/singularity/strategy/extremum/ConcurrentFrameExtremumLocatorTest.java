package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CumulativeCandleIndexProvider;
import com.siberalt.singularity.strategy.market.DefaultCandleIndexProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentFrameExtremumLocatorTest {
    private final CumulativeCandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();

    @Test
    void locateReturnsEmptyListWhenCandlesIsEmpty() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);

        List<Candle> result = locator.locate(Collections.emptyList(), candleIndexProvider);

        assertTrue(result.isEmpty());
        verify(baseLocator, never()).locate(anyList(), any());
    }

    @Test
    void locatesAllNonOverlappingExtremums() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum1 = Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 200);
        Candle extremum2 = Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 150),
            extremum1,
            Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 120),
            Candle.of(Instant.parse("2023-01-01T00:05:00Z"), 110)
        );
        List<Candle> frame2 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:06:00Z"), 250),
            Candle.of(Instant.parse("2023-01-01T00:07:00Z"), 250),
            extremum2,
            Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 250),
            Candle.of(Instant.parse("2023-01-01T00:10:00Z"), 250)
        );

        when(baseLocator.locate(eq(frame1), any())).thenReturn(List.of(extremum1));
        when(baseLocator.locate(eq(frame2), any())).thenReturn(List.of(extremum2));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        candleIndexProvider.accumulate(frame1);
        candleIndexProvider.accumulate(frame2);
        List<Candle> candles = Stream.of(frame1, frame2).flatMap(List::stream).toList();
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(2, result.size());
        verify(baseLocator, times(2)).locate(anyList(), any());
        assertEquals(extremum1, result.get(0));
        assertEquals(extremum2, result.get(1));
    }

    @Test
    void locateHandlesSingleFrameCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200);
        when(baseLocator.locate(anyList(), any())).thenReturn(List.of(extremum));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(3, baseLocator, 5, 0);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150),
            extremum
        );
        candleIndexProvider.accumulate(candles);
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(1, result.size());
        verify(baseLocator, times(1)).locate(anyList(), any());
    }

    @Test
    void locateHandlesPartialFrameCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200);
        when(baseLocator.locate(anyList(), any())).thenReturn(List.of(extremum));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);

        List<Candle> candles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150),
            extremum,
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 150),
            Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 100)
        );
        candleIndexProvider.accumulate(candles);
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(0, result.size());
        verify(baseLocator, times(0)).locate(anyList(), any());
    }

    @Test
    void locateHandlesSeveralPartsOfFrameCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum1 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200);
        Candle extremum2 = Candle.of(Instant.parse("2023-01-01T00:06:00Z"), 300);
        when(baseLocator.locate(anyList(), any()))
            .thenReturn(List.of(extremum2));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);

        List<Candle> framePart1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150),
            extremum1,
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 150),
            extremum2
        );
        List<Candle> framePart2 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:05:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:07:00Z"), 150),
            Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 150),
            Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 100)
        );
        candleIndexProvider.accumulate(framePart1);
        candleIndexProvider.accumulate(framePart2);

        List<Candle> result1 = locator.locate(framePart1, candleIndexProvider);
        assertEquals(0, result1.size());
        verify(baseLocator, times(0)).locate(anyList(), any());

        List<Candle> result2 = locator.locate(framePart2, candleIndexProvider);
        assertEquals(1, result2.size());
        assertEquals(extremum2, result2.get(0));
        verify(baseLocator, times(1)).locate(anyList(), any());
    }

//    @Test
//    void locateFiltersExtremumOnEndOfPartialFrameCorrectly() {
//        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
//        Candle extremum1 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200);
//        Candle falseExtremum = Candle.of(Instant.parse("2023-01-01T00:06:00Z"), 10);
//        when(baseLocator.locate(anyList(), any()))
//            .thenReturn(List.of(extremum1))
//            .thenReturn(List.of(falseExtremum));
//
//        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(15, baseLocator);
//
//        List<Candle> framePart1 = List.of(
//            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100),
//            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150),
//            extremum1,
//            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 150),
//            Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 100)
//        );
//        List<Candle> framePart2 = List.of(
//            Candle.of(Instant.parse("2023-01-01T00:05:00Z"), 100),
//            Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 100),
//            Candle.of(Instant.parse("2023-01-01T00:07:00Z"), 150),
//            Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 150),
//            falseExtremum
//        );
//        candleIndexProvider.accumulate(framePart1);
//        candleIndexProvider.accumulate(framePart2);
//        List<Candle> result = locator.locate(framePart1, candleIndexProvider);
//
//        assertEquals(1, result.size());
//        verify(baseLocator, times(1)).locate(anyList(), any());
//
//        List<Candle> result2 = locator.locate(framePart2, candleIndexProvider);
//        assertEquals(0, result2.size());
//        verify(baseLocator, times(2)).locate(anyList(), any());
//    }

    @Test
    void locateThrowsRuntimeExceptionOnExecutionError() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        when(baseLocator.locate(anyList(), any())).thenThrow(new RuntimeException("Test exception"));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(3, baseLocator);

        List<Candle> candles = List.of(new Candle(), new Candle(), new Candle());

        assertThrows(RuntimeException.class, () -> locator.locate(candles, candleIndexProvider));
    }

    @Test
    void locatesAllOverlappingExtremums() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum1 = Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 200);
        Candle extremum2 = Candle.of(Instant.parse("2023-01-01T00:07:00Z"), 300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 110),
            Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 150),
            extremum1
        );
        List<Candle> frame2 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:05:00Z"), 180),
            Candle.of(Instant.parse("2023-01-01T00:06:00Z"), 170),
            extremum2,
            Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 250),
            Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 280)
        );

        when(baseLocator.locate(any(), any())).thenReturn(List.of(extremum1));
        when(baseLocator.locate(eq(frame1), any())).thenReturn(List.of(extremum1));
        when(baseLocator.locate(eq(frame2), any())).thenReturn(List.of(extremum2));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        List<Candle> candles = Stream.of(frame1, frame2).flatMap(List::stream).toList();
        candleIndexProvider.accumulate(candles);
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(2, result.size());
        assertTrue(result.contains(extremum1));
        assertTrue(result.contains(extremum2));
    }

    @Test
    void locateHandlesEmptyOverlapGracefully() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200);

        List<Candle> frame = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150),
            extremum
        );

        when(baseLocator.locate(eq(frame), any())).thenReturn(List.of(extremum));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(3, baseLocator, 2, 0);

        candleIndexProvider.accumulate(frame);
        List<Candle> result = locator.locate(frame, candleIndexProvider);

        assertEquals(1, result.size());
        assertEquals(extremum, result.get(0));
    }

    @Test
    void locateFiltersFalseExtremumsCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle falseExtremum = Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 260);
        Candle realExtremum = Candle.of(Instant.parse("2023-01-01T00:07:00Z"),  300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"),  100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"),  150),
            Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200),
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 250),
            falseExtremum
        );
        List<Candle> frame2 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:05:00Z"),  270),
            Candle.of(Instant.parse("2023-01-01T00:06:00Z"),  150),
            realExtremum,
            Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 250),
            Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 280)
        );

        when(baseLocator.locate(any(), any())).thenReturn(Collections.emptyList());
        when(baseLocator.locate(eq(frame1), any())).thenReturn(List.of(falseExtremum));
        when(baseLocator.locate(eq(frame2), any())).thenReturn(List.of(realExtremum));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);
        candleIndexProvider.accumulate(Stream.of(frame1, frame2).flatMap(List::stream).toList());

        List<Candle> result1 = locator.locate(frame1, candleIndexProvider);
        assertEquals(0, result1.size());

        List<Candle> result2 = locator.locate(frame2, candleIndexProvider);
        assertEquals(1, result2.size());
        assertEquals(realExtremum, result2.get(0));
    }

    @Test
    void locateFiltersFalseEdgeExtremumsCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle falseExtremum = Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 260);
        Candle realExtremum = Candle.of(Instant.parse("2023-01-01T00:05:00Z"),  300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"),  100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"),  150),
            Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200),
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 250),
            falseExtremum
        );
        List<Candle> frame2 = List.of(
            realExtremum,
            Candle.of(Instant.parse("2023-01-01T00:06:00Z"),  270),
            Candle.of(Instant.parse("2023-01-01T00:07:00Z"),  150),
            Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 250),
            Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 280)
        );

        when(baseLocator.locate(any(), any())).thenReturn(Collections.emptyList());
        when(baseLocator.locate(eq(frame1), any())).thenReturn(List.of(falseExtremum));
        when(baseLocator.locate(eq(frame2), any())).thenReturn(List.of(realExtremum));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);
        candleIndexProvider.accumulate(Stream.of(frame1, frame2).flatMap(List::stream).toList());

        List<Candle> result1 = locator.locate(frame1, candleIndexProvider);
        assertEquals(0, result1.size());

        List<Candle> result2 = locator.locate(frame2, candleIndexProvider);
        assertEquals(1, result2.size());
        assertEquals(realExtremum, result2.get(0));
    }

    @Test
    void locateFiltersFalseLastExtremum() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle falseExtremum = Candle.of(Instant.parse("2023-01-01T00:09:00Z"), 260);
        Candle realExtremum = Candle.of(Instant.parse("2023-01-01T00:04:00Z"),  300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"),  100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"),  150),
            Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200),
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 250),
            realExtremum
        );
        List<Candle> frame2 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:05:00Z"),  270),
            Candle.of(Instant.parse("2023-01-01T00:06:00Z"),  150),
            Candle.of(Instant.parse("2023-01-01T00:07:00Z"), 250),
            Candle.of(Instant.parse("2023-01-01T00:08:00Z"), 280),
            falseExtremum
        );

        when(baseLocator.locate(any(), any()))
            .thenReturn(Collections.emptyList())
            .thenReturn(List.of(realExtremum))
            .thenReturn(List.of(falseExtremum));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator, 5, 2);
        candleIndexProvider.accumulate(Stream.of(frame1, frame2).flatMap(List::stream).toList());

        List<Candle> result1 = locator.locate(frame1, candleIndexProvider);
        assertEquals(0, result1.size());

        List<Candle> result2 = locator.locate(frame2, candleIndexProvider);
        assertEquals(1, result2.size());
        assertEquals(realExtremum, result2.get(0));
    }

    @Test
    void locateHandlesLargeInputEfficiently() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            candles.add(
                Candle.of(Instant.parse("2023-01-01T00:00:00Z").plusSeconds(i * 60), 100)
            );
        }

        when(baseLocator.locate(anyList(), any())).thenReturn(Collections.emptyList());

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(100, baseLocator);

        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertTrue(result.isEmpty());
        verify(baseLocator, times(10)).locate(anyList(), any());
    }
}
