package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.presenter.google.series.SeriesChunk;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MacdChartTest {
    private List<Candle> minutes(int count) {
        List<Candle> candles = new ArrayList<>(count);
        Instant open = Instant.parse("2026-02-09T07:00:00Z");

        for (int at = 0; at < count; at++) {
            double close = 100 + at;
            candles.add(Candle.of(open.plusSeconds(60L * at), 100, close, close, close, close));
        }

        return candles;
    }

    @Test
    void should_LayOutTheLineTheSignalAndTheHistogramOnARowPerBar() {
        SeriesChunk chunk = new MacdChart(1).setPeriods(2, 4, 3).chunkOf(minutes(12));

        // columns: time, line, signal, histogram
        assertEquals(4, chunk.columns().size());
        assertEquals(12, chunk.data().length);
        assertEquals(4, chunk.options().size());
        assertEquals("bars", chunk.options().get(3).get("type"));
    }

    @Test
    void should_LeaveAGap_WhereThereIsNoReadingYet() {
        Object[][] data = new MacdChart(1).setPeriods(2, 4, 3).chunkOf(minutes(12)).data();

        assertNull(data[2][1]);
        assertNotNull(data[3][1]);
        assertNull(data[4][2]);
        assertNotNull(data[5][2]);
        assertNull(data[4][3]);
        assertNotNull(data[5][3]);
    }

    @Test
    void should_Refuse_WhenThereIsNothingToDraw() {
        assertThrows(IllegalArgumentException.class, () -> new MacdChart(1).chunkOf(List.of()));
    }
}
