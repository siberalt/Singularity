package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MACD: линия, её средняя и зазор между ними. */
class MacdTest {
    @Test
    void readsNothingUntilBothAveragesAreReady() {
        Macd macd = new Macd(3, 6, 2);

        for (int at = 0; at < 5; at++) {
            macd.add(100.0 + at);
        }

        assertFalse(macd.ready());
        assertTrue(Double.isNaN(macd.line()));
        assertTrue(Double.isNaN(macd.histogram()));
    }

    /** На растущем ряду быстрая средняя выше медленной, значит линия положительна. */
    @Test
    void putsTheLineAboveZeroWhenPricesRise() {
        Macd macd = new Macd(3, 6, 2);

        for (int at = 0; at < 40; at++) {
            macd.add(100.0 + 2 * at);
        }

        assertTrue(macd.ready());
        assertTrue(macd.line() > 0, "линия должна быть положительной: " + macd.line());
    }

    @Test
    void putsTheLineBelowZeroWhenPricesFall() {
        Macd macd = new Macd(3, 6, 2);

        for (int at = 0; at < 40; at++) {
            macd.add(300.0 - 2 * at);
        }

        assertTrue(macd.line() < 0, "линия должна быть отрицательной: " + macd.line());
    }

    /**
     * Линия - это в точности разность двух EMA, и это стоит зафиксировать: иначе любая правка затравки
     * у {@link Ema} разошлась бы с MACD незаметно.
     */
    @Test
    void isExactlyTheDifferenceOfTwoEmas() {
        List<Candle> candles = seriesOf(60, at -> 100 + Math.sin(at / 4.0) * 10);
        Macd macd = new Macd(12, 26, 9);

        for (Candle candle : candles) {
            macd.add(candle);
        }

        double fast = Ema.of(candles, 12);
        double slow = Ema.of(candles, 26);

        assertEquals(fast - slow, macd.line(), 1e-9);
    }

    /** Гистограмма меняет знак позже линии: она про ускорение отрыва, а не про сам отрыв. */
    @Test
    void sharesTheSeriesWithItsStaticForm() {
        List<Candle> candles = seriesOf(80, at -> 100 + 3.0 * at);
        double[] histogram = Macd.seriesOf(candles, 12, 26, 9);
        double[] line = Macd.lineSeriesOf(candles, 12, 26, 9);
        Macd macd = new Macd(12, 26, 9);

        for (Candle candle : candles) {
            macd.add(candle);
        }

        assertEquals(macd.histogram(), histogram[histogram.length - 1], 1e-12);
        assertEquals(macd.line(), line[line.length - 1], 1e-12);
    }

    @Test
    void refusesPeriodsThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class, () -> new Macd(26, 12, 9));
        assertThrows(IllegalArgumentException.class, () -> new Macd(0, 26, 9));
        assertThrows(IllegalArgumentException.class, () -> new Macd(12, 26, 0));
    }

    private static List<Candle> seriesOf(int count, IntToDoubleFunction price) {
        List<Candle> candles = new ArrayList<>(count);

        for (int at = 0; at < count; at++) {
            Quotation value = Quotation.of(price.applyAsDouble(at));

            candles.add(new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
                value, value, value, value, 1));
        }

        return candles;
    }
}
