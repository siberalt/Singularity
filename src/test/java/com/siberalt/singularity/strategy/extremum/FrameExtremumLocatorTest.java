package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.market.DefaultCandleIndexProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class FrameExtremumLocatorTest {
    private final CandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();

    @Test
    void locateReturnsEmptyListWhenInputIsEmpty() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        FrameExtremumLocator locator = new FrameExtremumLocator(5, baseLocator);

        List<Candle> result = locator.locate(Collections.emptyList(), candleIndexProvider);

        assertTrue(result.isEmpty());
        verify(baseLocator, never()).locate(anyList(), any());
    }

    @Test
    void locateProcessesFramesOfExactSize() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 15, 17, 14, 16, 200);

        List<Candle> frame = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150),
            extremum
        );

        when(baseLocator.locate(frame, candleIndexProvider)).thenReturn(List.of(extremum));

        FrameExtremumLocator locator = new FrameExtremumLocator(3, baseLocator);

        List<Candle> result = locator.locate(frame, candleIndexProvider);

        assertEquals(1, result.size());
        assertEquals(extremum, result.get(0));
        verify(baseLocator, times(1)).locate(anyList(), eq(candleIndexProvider));
    }

    @Test
    void locateProcessesFramesWithRemainingCandles() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum1 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 15, 17, 14, 16, 200);
        Candle extremum2 = Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 17, 19, 16, 18, 300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150),
            extremum1
        );
        List<Candle> remainingCandles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 16, 18, 15, 17, 250),
            extremum2
        );

        when(baseLocator.locate(frame1, candleIndexProvider)).thenReturn(List.of(extremum1));
        when(baseLocator.locate(remainingCandles, candleIndexProvider)).thenReturn(List.of(extremum2));

        FrameExtremumLocator locator = new FrameExtremumLocator(3, baseLocator);

        List<Candle> candles = Stream.concat(frame1.stream(), remainingCandles.stream()).toList();
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(2, result.size());
        assertEquals(extremum1, result.get(0));
        assertEquals(extremum2, result.get(1));
        verify(baseLocator, times(2)).locate(anyList(), eq(candleIndexProvider));
    }

    @Test
    void locateHandlesSingleCandleFrame() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        Candle extremum = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100);

        when(baseLocator.locate(anyList(), eq(candleIndexProvider))).thenReturn(List.of(extremum));

        FrameExtremumLocator locator = new FrameExtremumLocator(1, baseLocator);

        List<Candle> candles = List.of(extremum);
        List<Candle> result = locator.locate(candles, candleIndexProvider);

        assertEquals(1, result.size());
        assertEquals(extremum, result.get(0));
        verify(baseLocator, times(1)).locate(anyList(), eq(candleIndexProvider));
    }

    @Test
    void locateThrowsRuntimeExceptionWhenBaseLocatorFails() {
        ExtremumLocator baseLocator = mock(ExtremumLocator.class);
        when(baseLocator.locate(anyList(), eq(candleIndexProvider))).thenThrow(new RuntimeException("Test exception"));

        FrameExtremumLocator locator = new FrameExtremumLocator(3, baseLocator);

        List<Candle> candles = List.of(new Candle(), new Candle(), new Candle());

        assertThrows(RuntimeException.class, () -> locator.locate(candles, candleIndexProvider));
    }
}
