package com.siberalt.singularity.strategy.extreme;

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

class ConcurrentFrameExtremeLocatorTest {
    private final CandleFactory candleFactory = new CandleFactory("TEST_INSTRUMENT");

    @Test
    void locateReturnsEmptyListWhenCandlesIsEmpty() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(10, baseLocator);

        List<Candle> result = locator.locate(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(baseLocator, never()).locate(anyList());
    }

    @Test
    void locatesOrdinaryExtremes() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

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

        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(5, baseLocator);

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

        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        when(baseLocator.locate(candles)).thenReturn(List.of(extreme));

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(3)
            .setExtremeVicinity(0)
            .build();

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
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        Candle extreme = candles.get(2); // Candle with value 200 and time "2023-01-01T00:02:00Z"
        when(baseLocator.locate(anyList())).thenReturn(List.of(extreme));

        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(10, baseLocator);
        List<Candle> result = locator.locate(candles);

        assertEquals(0, result.size()); // No full frames to process
        verify(baseLocator, times(0)).locate(anyList());
    }

    @Test
    void locateHandlesNonZeroStartIndexIsOutsideRange() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        CandleFactory candleFactory = new CandleFactory("TEST_INSTRUMENT", 7);

        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:07:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 110),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:10:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:11:00Z", 200)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:12:00Z", 180),
            candleFactory.createCommon("2023-01-01T00:13:00Z", 170),
            candleFactory.createCommon("2023-01-01T00:14:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:15:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:16:00Z", 280)
        );
        List<Candle> candles = ListUtils.merge(frame1, frame2);
        Candle extreme1 = frame1.get(2); // Candle with value 100 and time "2023-01-01T00:09:00Z"
        Candle extreme2 = frame2.get(2); // Candle with value 300 and time "2023-01-01T00:14:00Z";

        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(extreme1));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(extreme2));

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(5)
            .setStartIndex(2)
            .build();

        List<Candle> result = locator.locate(candles);

        assertEquals(2, result.size());
        assertTrue(result.contains(extreme1));
        assertTrue(result.contains(extreme2));
    }

    @Test
    void locateHandlesNonZeroStartIndexForFullFrames() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

        List<Candle> beforeFrames = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 90),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 95)
        );
        List<Candle> frame1 = List.of(
            candleFactory.createCommon("2023-01-01T00:02:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 110),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:05:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 200)
        );
        List<Candle> frame2 = List.of(
            candleFactory.createCommon("2023-01-01T00:07:00Z", 180),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 170),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:10:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:11:00Z", 280)
        );
        List<Candle> afterFrames = List.of(
            candleFactory.createCommon("2023-01-01T00:12:00Z", 260),
            candleFactory.createCommon("2023-01-01T00:13:00Z", 240),
            candleFactory.createCommon("2023-01-01T00:14:00Z", 240)
        );
        List<Candle> candles = ListUtils.merge(beforeFrames, frame1, frame2, afterFrames);
        Candle extreme1 = frame1.get(4); // Candle with value 200 and time "2023-01-01T00:04:00Z"
        Candle extreme2 = frame2.get(2); // Candle with value 300 and time "2023-01-01T00:07:00Z";

        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(extreme1));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(extreme2));
        when(baseLocator.locate(candles.subList(3, 11))).thenReturn(List.of(extreme1));

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(5)
            .setStartIndex(2)
            .build();

        List<Candle> result = locator.locate(candles);

        assertEquals(2, result.size());
        assertTrue(result.contains(extreme1));
        assertTrue(result.contains(extreme2));
    }

    @Test
    void locateHandlesNonZeroStartIndexForPartialFrameCorrectly() {
        List<Candle> allCandles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:05:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 100)
        );

        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(10)
            .setStartIndex(4)
            .build();

        List<Candle> result = locator.locate(allCandles);
        assertEquals(0, result.size());
        verify(baseLocator, times(0)).locate(anyList());
    }

    @Test
    void locateThrowsRuntimeExceptionOnExecutionError() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        when(baseLocator.locate(anyList())).thenThrow(new RuntimeException("Test exception"));

        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(3, baseLocator);

        List<Candle> candles = List.of(new Candle(), new Candle(), new Candle());

        assertThrows(RuntimeException.class, () -> locator.locate(candles));
    }

    @Test
    void locateHandlesZeroVicinityGracefully() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

        List<Candle> frame = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200)
        );
        Candle extreme = frame.get(2); // Candle with value 200 and time "2023-01-01T00:02:00Z"

        when(baseLocator.locate(eq(frame))).thenReturn(List.of(extreme));

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(3)
            .setThreadCount(2)
            .setExtremeVicinity(0)
            .build();

        List<Candle> result = locator.locate(frame);

        assertEquals(1, result.size());
        assertEquals(extreme, result.get(0));
    }

    @Test
    void locateFiltersFalseExtremesCorrectly() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

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

        List<Candle> allCandles = ListUtils.merge(frame1, frame2);

        when(baseLocator.locate(allCandles.subList(1, 9))).thenReturn(List.of(realExtreme));
        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(falseExtreme));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(realExtreme));

        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(5, baseLocator);

        List<Candle> result = locator.locate(allCandles);
        assertEquals(1, result.size());
        assertEquals(realExtreme, result.get(0));
    }

    @Test
    void locateChoosesEdgeExtremesCorrectly() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

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
        List<Candle> allCandles = ListUtils.merge(frame1, frame2);
        Candle falseExtreme = frame1.get(4); // Candle with value 260 and time "2023-01-01T00:04:00Z"
        Candle realExtreme = frame2.get(0); // Candle with value 300 and time "2023-01-01T00:05:00Z"

        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(falseExtreme));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(realExtreme));
        when(baseLocator.locate(allCandles.subList(1, 9))).thenReturn(List.of(realExtreme));

        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(5, baseLocator);

        List<Candle> result = locator.locate(allCandles);
        assertEquals(1, result.size());
        assertEquals(realExtreme, result.get(0));
    }

    @Test
    void locateFiltersFalseLastExtreme() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

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
        List<Candle> allCandles = ListUtils.merge(frame1, frame2);
        Candle falseExtreme = frame2.get(4); // Candle with value 260 and time "2023-01-01T00:09:00Z"
        Candle realExtreme = frame1.get(4); // Candle with value 300 and time "2023-01-01T00:04:00Z"

        when(baseLocator.locate(eq(frame1))).thenReturn(List.of(realExtreme));
        when(baseLocator.locate(eq(frame2))).thenReturn(List.of(falseExtreme));
        when(baseLocator.locate(allCandles.subList(1, 9))).thenReturn(List.of(realExtreme));

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(5)
            .setExtremeVicinity(2)
            .build();

        List<Candle> result = locator.locate(ListUtils.merge(frame1, frame2));
        assertEquals(1, result.size());
        assertEquals(realExtreme, result.get(0));
    }

    @Test
    void locateHandlesLargeInputEfficiently() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            candles.add(
                candleFactory.createCommon(
                    Instant.parse("2023-01-01T00:00:00Z").plusSeconds(i * 60), 100
                )
            );
        }

        when(baseLocator.locate(anyList())).thenReturn(Collections.emptyList());
        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(100, baseLocator);

        List<Candle> result = locator.locate(candles);

        assertTrue(result.isEmpty());
        verify(baseLocator, times(10)).locate(anyList());
    }

    @Test
    void locateHandlesSlidingWindowCorrectly() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);

        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:01:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:02:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:03:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:04:00Z", 300),
            candleFactory.createCommon("2023-01-01T00:05:00Z", 250),
            candleFactory.createCommon("2023-01-01T00:06:00Z", 200),
            candleFactory.createCommon("2023-01-01T00:07:00Z", 150),
            candleFactory.createCommon("2023-01-01T00:08:00Z", 100),
            candleFactory.createCommon("2023-01-01T00:09:00Z", 100)
        );

        // Define extremes for overlapping frames
        Candle extreme1 = candles.get(1); // Value 300
        Candle extreme2 = candles.get(4); // Value 300
        Candle extreme3 = candles.get(7); // Value 300

        List<Candle> window1 = candles.subList(0, 6); // 00:00 to 00:05
        List<Candle> window2 = candles.subList(2, 8); // 00:02 to 00:07
        List<Candle> window3 = candles.subList(3, 9); // 00:03 to 00:08

        List<Candle> windowExtremes1 = List.of(extreme1, extreme2);
        List<Candle> windowExtremes2 = List.of(extreme2);
        List<Candle> windowExtremes3 = List.of(extreme2, extreme3);

        when(baseLocator.locate(candles.subList(0, 3))).thenReturn(List.of(extreme1));
        when(baseLocator.locate(candles.subList(3, 6))).thenReturn(List.of(extreme2));
        when(baseLocator.locate(candles.subList(6, 9))).thenReturn(List.of(extreme3));

        ConcurrentFrameExtremeLocator locator = ConcurrentFrameExtremeLocator.builder(baseLocator)
            .setFrameSize(3)
            .setExtremeVicinity(0)
            .build();

        List<Candle> result = locator.locate(window1);
        assertEquals(windowExtremes1, result);

        result = locator.locate(window2);
        assertEquals(windowExtremes2, result);

        result = locator.locate(window3);
        assertEquals(windowExtremes3, result);

        verify(baseLocator, times(1)).locate(candles.subList(0, 3));
        verify(baseLocator, times(3)).locate(candles.subList(3, 6));
        verify(baseLocator, times(1)).locate(candles.subList(6, 9));
    }
}
