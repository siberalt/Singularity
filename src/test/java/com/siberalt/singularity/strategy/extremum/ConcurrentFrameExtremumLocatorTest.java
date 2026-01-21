package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.utils.ListUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentFrameExtremumLocatorTest {
    private final CandleFactory candleFactory = new CandleFactory("TEST_INSTRUMENT");
    @Test
    void locateReturnsEmptyListWhenCandlesIsEmpty() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);

        List<Candle> result = locator.locate(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(baseLocator, never()).locate(anyList());
    }

    @Test
    void locatesAllNonOverlappingExtremes() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);

        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:01:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 120),
            candleFactory.createCommon("2023-01-01T00:05:00Z", 110)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:06:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:10:00Z", 250)
        );

        Candle extreme1 = frame1.get(2); // Candle with value 200 and time "2023-01-01T00:03:00Z"
        Candle extreme2 = frame2.get(2); // Candle with value 300 and time "2023-01-01T00:08:00Z"

        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(extreme1));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(extreme2));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        List<Candle> candles = ListUtils.merge(frame1, frame2);
        List<Candle> result = locator.locate(candles);

        assertEquals(2, result.size());
        verify(baseLocator, times(2)).locate(anyList());
        assertEquals(extreme1, result.get(0));
        assertEquals(extreme2, result.get(1));
    }

    @Test
    void locateHandlesSingleFrameCorrectly() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200)
        );
        Candle extreme = candles.get(2); // Candle with value 200 and time "2023-01-01T00:02:00Z"

        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        when(baseLocator.locate(anyList())).thenReturn(List.of(extreme));
        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(
            3, baseLocator, 5, 0
        );

        List<Candle> result = locator.locate(candles);

        assertEquals(1, result.size());
        verify(baseLocator, times(1)).locate(anyList());
    }

    @Test
    void locateHandlesPartialFrameCorrectly() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 100)
        );
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extreme = candles.get(2); // Candle with value 200 and time "2023-01-01T00:02:00Z"
        when(baseLocator.locate(anyList())).thenReturn(List.of(extreme));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);
        List<Candle> result = locator.locate(candles);

        assertEquals(0, result.size());
        verify(baseLocator, times(0)).locate(anyList());
    }

    @Test
    void locateHandlesSeveralPartsOfFrameCorrectly() {
        List<Candle> framePart1 = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 300)
        );
        List<Candle> framePart2 = List.of(
            candleFactory.createCommon("2023-01-01T00:05:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 100)
        );

        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extreme = framePart1.get(4); // Candle with value 300 and time "2023-01-01T00:04:00Z"
        when(baseLocator.locate(anyList())).thenReturn(List.of(extreme));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);

        List<Candle> result1 = locator.locate(framePart1);
        assertEquals(0, result1.size());
        verify(baseLocator, times(0)).locate(anyList());

        List<Candle> result2 = locator.locate(framePart2);
        assertEquals(1, result2.size());
        assertEquals(extreme, result2.get(0));
        verify(baseLocator, times(1)).locate(anyList());
    }

    @Test
    void locateThrowsRuntimeExceptionOnExecutionError() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        when(baseLocator.locate(anyList())).thenThrow(new RuntimeException("Test exception"));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(3, baseLocator);

        List<Candle> candles = List.of(new Candle(), new Candle(), new Candle());

        assertThrows(RuntimeException.class, () -> locator.locate(candles));
    }

    @Test
    void locatesAllOverlappingExtremes() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);

        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 110),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 200)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:05:00Z", 180),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 170),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 280)
        );
        Candle extreme1 = frame1.get(4); // Candle with value 200 and time "2023-01-01T00:04:00Z"
        Candle extreme2 = frame2.get(2); // Candle with value 300 and time "2023-01-01T00:07:00Z";

        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(extreme1));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(extreme2));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        List<Candle> candles = ListUtils.merge(frame1, frame2);
        List<Candle> result = locator.locate(candles);

        assertEquals(2, result.size());
        assertTrue(result.contains(extreme1));
        assertTrue(result.contains(extreme2));
    }

    @Test
    void locateHandlesEmptyOverlapGracefully() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);

        List<Candle> frame = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200)
        );
        Candle extreme = frame.get(2); // Candle with value 200 and time "2023-01-01T00:02:00Z"

        when(baseLocator.locate(eq(frame))).thenReturn(List.of(extreme));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(
            3, baseLocator, 2, 0
        );
        List<Candle> result = locator.locate(frame);

        assertEquals(1, result.size());
        assertEquals(extreme, result.get(0));
    }

    @Test
    void locateFiltersFalseExtremesCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);


        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 260)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:05:00Z", 270),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 280)
        );
        Candle falseExtreme = frame1.get(4); // Candle with value 260 and time "2023-01-01T00:04:00Z"
        Candle realExtreme = frame2.get(2); // Candle with value 300 and time "2023-01-01T00:07:00Z"

        when(baseLocator.locate(any())).thenReturn(Collections.emptyList());
        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(falseExtreme));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(realExtreme));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        List<Candle> result1 = locator.locate(frame1);
        assertEquals(0, result1.size());

        List<Candle> result2 = locator.locate(frame2);
        assertEquals(1, result2.size());
        assertEquals(realExtreme, result2.get(0));
    }

    @Test
    void locateFiltersFalseEdgeExtremesCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);

        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 260)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:05:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 270),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 280)
        );
        Candle falseExtreme = frame1.get(4); // Candle with value 260 and time "2023-01-01T00:04:00Z"
        Candle realExtreme = frame2.get(0); // Candle with value 300 and time "2023-01-01T00:05:00Z"

        when(baseLocator.locate(any())).thenReturn(Collections.emptyList());
        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(falseExtreme));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(realExtreme));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        List<Candle> result1 = locator.locate(frame1);
        assertEquals(0, result1.size());

        List<Candle> result2 = locator.locate(frame2);
        assertEquals(1, result2.size());
        assertEquals(realExtreme, result2.get(0));
    }

    @Test
    void locateFiltersFalseLastExtreme() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);

        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 300)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:05:00Z", 270),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 280),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 260)
        );
        Candle falseExtreme = frame2.get(4); // Candle with value 260 and time "2023-01-01T00:09:00Z"
        Candle realExtreme = frame1.get(4); // Candle with value 300 and time "2023-01-01T00:04:00Z"

        when(baseLocator.locate(any()))
            .thenReturn(Collections.emptyList())
            .thenReturn(List.of(realExtreme))
            .thenReturn(List.of(falseExtreme));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(
            5, baseLocator, 5, 2
        );

        List<Candle> result1 = locator.locate(frame1);
        assertEquals(0, result1.size());

        List<Candle> result2 = locator.locate(frame2);
        assertEquals(1, result2.size());
        assertEquals(realExtreme, result2.get(0));
    }

    @Test
    void locateHandlesLargeInputEfficiently() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            candles.add(
                candleFactory.createCommon(
                    Instant.parse("2023-01-01T00:00:00Z").plusSeconds(i * 60), 100
                )
            );
        }

        when(baseLocator.locate(anyList())).thenReturn(Collections.emptyList());
        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(100, baseLocator);

        List<Candle> result = locator.locate(candles);

        assertTrue(result.isEmpty());
        verify(baseLocator, times(10)).locate(anyList());
    }
}
