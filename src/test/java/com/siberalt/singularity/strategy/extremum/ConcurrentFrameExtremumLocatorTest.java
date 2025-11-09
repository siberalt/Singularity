package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConcurrentFrameExtremumLocatorTest {

    @Test
    void locateReturnsEmptyListWhenCandlesIsEmpty() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(10, baseLocator);

        List<Candle> result = locator.locate(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(baseLocator, never()).locate(anyList());
    }

    @Test
    void locateProcessesAllFramesCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum1 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 15, 17, 14, 16, 200);
        Candle extremum2 = Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 17, 19, 16, 18, 300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150),
            extremum1
        );
        List<Candle> frame2 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 16, 18, 15, 17, 250),
            extremum2
        );

        when(baseLocator.locate(frame1)).thenReturn(List.of(extremum1));
        when(baseLocator.locate(frame2)).thenReturn(List.of(extremum2));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(3, baseLocator);

        List<Candle> candles = Stream.of(frame1, frame2).flatMap(List::stream).toList();
        List<Candle> result = locator.locate(candles);

        assertEquals(2, result.size());
        verify(baseLocator, times(2)).locate(anyList());
        assertEquals(extremum1, result.get(0));
        assertEquals(extremum2, result.get(1));
    }

    @Test
    void locateHandlesSingleFrameCorrectly() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        when(baseLocator.locate(anyList())).thenReturn(List.of(new Candle()));

        ConcurrentFrameExtremumLocator locator = new ConcurrentFrameExtremumLocator(5, baseLocator);

        List<Candle> candles = List.of(new Candle(), new Candle(), new Candle());
        List<Candle> result = locator.locate(candles);

        assertEquals(1, result.size());
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
}
