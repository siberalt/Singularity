package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

public class FrameExtremesLocatorTest {
    @Test
    void locateReturnsEmptyListWhenInputIsEmpty() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        FrameExtremeLocator locator = new FrameExtremeLocator(5, baseLocator);

        List<Candle> result = locator.locate(Collections.emptyList());

        assertTrue(result.isEmpty());
        verify(baseLocator, never()).locate(anyList());
    }

    @Test
    void locateProcessesFramesOfExactSize() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        Candle extreme = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 15, 17, 14, 16, 200);

        List<Candle> frame = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150),
            extreme
        );

        when(baseLocator.locate(frame)).thenReturn(List.of(extreme));

        FrameExtremeLocator locator = new FrameExtremeLocator(3, baseLocator);

        List<Candle> result = locator.locate(frame);

        assertEquals(1, result.size());
        assertEquals(extreme, result.get(0));
        verify(baseLocator, times(1)).locate(anyList());
    }

    @Test
    void locateProcessesFramesWithRemainingCandles() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        Candle extreme1 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 15, 17, 14, 16, 200);
        Candle extreme2 = Candle.of(Instant.parse("2023-01-01T00:04:00Z"), 17, 19, 16, 18, 300);

        List<Candle> frame1 = List.of(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100),
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 14, 16, 13, 15, 150),
            extreme1
        );
        List<Candle> remainingCandles = List.of(
            Candle.of(Instant.parse("2023-01-01T00:03:00Z"), 16, 18, 15, 17, 250),
            extreme2
        );

        when(baseLocator.locate(frame1)).thenReturn(List.of(extreme1));
        when(baseLocator.locate(remainingCandles)).thenReturn(List.of(extreme2));

        FrameExtremeLocator locator = new FrameExtremeLocator(3, baseLocator);

        List<Candle> candles = Stream.concat(frame1.stream(), remainingCandles.stream()).toList();
        List<Candle> result = locator.locate(candles);

        assertEquals(2, result.size());
        assertEquals(extreme1, result.get(0));
        assertEquals(extreme2, result.get(1));
        verify(baseLocator, times(2)).locate(anyList());
    }

    @Test
    void locateHandlesSingleCandleFrame() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        Candle extreme = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 10, 15, 9, 14, 100);

        when(baseLocator.locate(anyList())).thenReturn(List.of(extreme));

        FrameExtremeLocator locator = new FrameExtremeLocator(1, baseLocator);

        List<Candle> candles = List.of(extreme);
        List<Candle> result = locator.locate(candles);

        assertEquals(1, result.size());
        assertEquals(extreme, result.get(0));
        verify(baseLocator, times(1)).locate(anyList());
    }

    @Test
    void locateThrowsRuntimeExceptionWhenBaseLocatorFails() {
        ExtremeLocator baseLocator = mock(ExtremeLocator.class);
        when(baseLocator.locate(anyList())).thenThrow(new RuntimeException("Test exception"));

        FrameExtremeLocator locator = new FrameExtremeLocator(3, baseLocator);

        List<Candle> candles = List.of(new Candle(), new Candle(), new Candle());

        assertThrows(RuntimeException.class, () -> locator.locate(candles));
    }
}
