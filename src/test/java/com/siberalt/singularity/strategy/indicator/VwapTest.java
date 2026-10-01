package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VwapTest {
    private Candle candle(double high, double low, double close, long volume) {
        return Candle.of(TimePoint.NULL, volume, 0, high, low, close);
    }

    private final List<Candle> candles = List.of(
        candle(101, 99, 100, 100),
        candle(102, 100, 101, 200),
        candle(104, 102, 103, 300)
    );

    @Test
    void should_WeighClosesByVolume() {
        // (100*100 + 101*200 + 103*300) / 600
        assertEquals(61_100.0 / 600, Vwap.of(candles), 1e-9);
    }

    @Test
    void should_UseTheGivenPrice() {
        // (101*100 + 102*200 + 104*300) / 600 with the highs
        assertEquals(61_700.0 / 600, Vwap.of(candles, Candle::getHighAsDouble), 1e-9);
    }

    @Test
    void should_SayNothing_WhenNoCandlesOrNoVolume() {
        assertTrue(Double.isNaN(Vwap.of(null)));
        assertTrue(Double.isNaN(Vwap.of(List.of())));
        assertTrue(Double.isNaN(Vwap.of(List.of(candle(100, 100, 100, 0), candle(101, 101, 101, 0)))));
        assertFalse(new Vwap().ready());
    }

    @Test
    void should_AgreeWithTheOneShotForm_WhenFedCandleByCandle() {
        var vwap = new Vwap();

        for (Candle candle : candles) {
            vwap.add(candle);
        }

        assertTrue(vwap.ready());
        assertEquals(Vwap.of(candles), vwap.value(), 1e-9);
    }

    @Test
    void should_GiveTheReadingAtEveryCandle_InASeries() {
        double[] series = Vwap.seriesOf(candles, Candle::getCloseAsDouble);

        assertEquals(3, series.length);
        assertEquals(100, series[0], 1e-9);
        assertEquals(30_200.0 / 300, series[1], 1e-9);
        assertEquals(Vwap.of(candles), series[2], 1e-9);
    }

    @Test
    void should_StartOverEveryDay_WhenAskedTo() {
        Instant day1 = Instant.parse("2023-03-25T10:00:00Z");
        Instant day2 = Instant.parse("2023-03-26T10:00:00Z");
        List<Candle> twoDays = List.of(
            Candle.of(day1, 100, 0, 100, 100, 100),
            Candle.of(day1.plusSeconds(60), 100, 0, 110, 110, 110),
            Candle.of(day2, 100, 0, 200, 200, 200),
            Candle.of(day2.plusSeconds(60), 100, 0, 220, 220, 220)
        );

        double[] running = Vwap.seriesOf(twoDays, Candle::getCloseAsDouble, false);
        double[] daily = Vwap.seriesOf(twoDays, Candle::getCloseAsDouble, true);

        assertEquals(105, running[1], 1e-9);
        assertEquals(410.0 / 3, running[2], 1e-9);
        assertEquals(157.5, running[3], 1e-9);
        assertEquals(105, daily[1], 1e-9);
        assertEquals(200, daily[2], 1e-9);
        assertEquals(210, daily[3], 1e-9);
    }

    @Test
    void should_WaitForVolume_AndThenReadFromIt() {
        var vwap = new Vwap();

        assertTrue(Double.isNaN(vwap.add(100, 0)));
        assertEquals(101, vwap.add(101, 10), 1e-9);
    }

    @Test
    void should_Forget_WhenReset() {
        var vwap = new Vwap();
        vwap.add(candles.getFirst());

        vwap.reset();

        assertFalse(vwap.ready());
        assertTrue(Double.isNaN(vwap.value()));
        assertEquals(103, vwap.add(candles.getLast()), 1e-9);
    }
}
