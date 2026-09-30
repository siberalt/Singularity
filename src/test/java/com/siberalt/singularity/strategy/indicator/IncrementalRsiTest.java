package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalRsiTest {
    private List<Candle> closes(double... closes) {
        List<Candle> candles = new ArrayList<>(closes.length);

        for (double close : closes) {
            candles.add(Candle.of(TimePoint.NULL, 100, close, close, close, close));
        }

        return candles;
    }

    @Test
    void should_SayNothing_UntilItHasSeenAPeriodOfChanges() {
        var rsi = new IncrementalRsi(3);

        assertTrue(Double.isNaN(rsi.add(100)));
        assertTrue(Double.isNaN(rsi.add(101)));
        assertFalse(rsi.ready());
        assertTrue(Double.isNaN(rsi.add(102)));
        assertFalse(rsi.ready());

        rsi.add(103);

        assertTrue(rsi.ready());
        assertFalse(Double.isNaN(rsi.value()));
    }

    /** Nothing but rises is a hundred, nothing but falls is nothing, and no movement is the middle. */
    @Test
    void should_ReadTheExtremes_WhenEveryChangeWentTheSameWay() {
        assertEquals(100, IncrementalRsi.of(closes(100, 101, 102, 103), 3), 1e-9);
        assertEquals(0, IncrementalRsi.of(closes(100, 99, 98, 97), 3), 1e-9);
        assertEquals(IncrementalRsi.BALANCED, IncrementalRsi.of(closes(100, 100, 100, 100), 3), 1e-9);
    }

    /**
     * Wilder's own arithmetic, by hand. Over the first three changes the gains average
     * (2 + 0 + 1) / 3 = 1 and the losses (0 + 4 + 0) / 3 = 4 / 3, so the reading is
     * 100 - 100 / (1 + 3 / 4) = 42.857.
     */
    @Test
    void should_AveragePlainly_OverTheFirstPeriodOfChanges() {
        assertEquals(42.857142857, IncrementalRsi.of(closes(100, 102, 98, 99), 3), 1e-6);
    }

    /**
     * And smoothing after it: the fourth change of +3 makes the gains (1 * 2 + 3) / 3 = 5 / 3 and the
     * losses (4 / 3 * 2) / 3 = 8 / 9, so the reading is 100 - 100 / (1 + 15 / 8) = 65.217.
     */
    @Test
    void should_SmoothEveryChange_AfterTheFirstPeriod() {
        assertEquals(65.217391304, IncrementalRsi.of(closes(100, 102, 98, 99, 102), 3), 1e-6);
    }

    /** Fed one at a time or all at once, the reading is the same. */
    @Test
    void should_ReadTheSame_FedBarByBarOrAsAList() {
        double[] prices = {100, 102, 98, 99, 102, 101, 104, 103, 99, 100};
        var rsi = new IncrementalRsi(4);

        for (double price : prices) {
            rsi.add(price);
        }

        assertEquals(rsi.value(), IncrementalRsi.of(closes(prices), 4), 1e-12);
    }

    @Test
    void should_GiveAReadingForEveryBar_AsASeries() {
        double[] series = IncrementalRsi.seriesOf(closes(100, 102, 98, 99, 102), 3);

        assertEquals(5, series.length);
        assertTrue(Double.isNaN(series[0]));
        assertTrue(Double.isNaN(series[2]));
        assertEquals(42.857142857, series[3], 1e-6);
        assertEquals(65.217391304, series[4], 1e-6);
    }

    /** The whole history counts, not the last period of it: smoothing carries every change forward. */
    @Test
    void should_DependOnTheWholeHistory_NotTheLastPeriodOfIt() {
        double shortHistory = IncrementalRsi.of(closes(100, 99, 102, 101), 3);
        double longHistory = IncrementalRsi.of(closes(90, 120, 100, 99, 102, 101), 3);

        assertFalse(Math.abs(shortHistory - longHistory) < 1e-9);
    }

    @Test
    void should_SayNothing_WhenThereAreNoCandlesAtAll() {
        assertTrue(Double.isNaN(IncrementalRsi.of(List.of(), 3)));
        assertTrue(Double.isNaN(IncrementalRsi.of(null, 3)));
        assertEquals(0, IncrementalRsi.seriesOf(List.of(), 3).length);
    }

    @Test
    void should_Throw_WhenThePeriodIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new IncrementalRsi(0));
        assertThrows(IllegalArgumentException.class, () -> new IncrementalRsi(-1));
    }
}
