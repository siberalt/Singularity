package com.siberalt.singularity.strategy.upside.trend;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Тренд по MACD: сторона в сигнале, нормированная гистограмма в силе. */
class MacdUpsideCalculatorTest {
    @Test
    void saysNothingUntilTheSlowAverageHasItsPeriod() {
        MacdUpsideCalculator macd = new MacdUpsideCalculator();

        assertEquals(Upside.NEUTRAL, macd.calculate(rising(20)));
        assertEquals(Upside.NEUTRAL, macd.calculate(null));
        assertEquals(Upside.NEUTRAL, macd.calculate(List.of()));
    }

    @Test
    void followsTheLineWhenAskedForTheLine() {
        MacdUpsideCalculator byLine = new MacdUpsideCalculator(MacdUpsideCalculator.Source.LINE);

        assertEquals(1, byLine.calculate(rising(80)).signal());
        assertEquals(-1, byLine.calculate(falling(80)).signal());
    }

    /**
     * Гистограмма - это ускорение, а не наклон, и на равномерном росте она сходится к нулю: линия
     * перестаёт меняться, её средняя догоняет, зазор исчезает. Поэтому ровный тренд читается как флэт - и
     * это не дефект, а то, что гистограмма означает. На ускоряющемся росте она положительна, на
     * замедляющемся отрицательна, хотя цена растёт в обоих случаях.
     */
    @Test
    void followsAccelerationRatherThanSlope() {
        assertEquals(0, new MacdUpsideCalculator().calculate(rising(120)).signal(),
            "равномерный рост для гистограммы - флэт");
        assertEquals(1, new MacdUpsideCalculator()
            .calculate(seriesOf(80, at -> 100 + 0.05 * at * at)).signal());
        assertEquals(-1, new MacdUpsideCalculator()
            .calculate(seriesOf(80, at -> 100 + 20 * Math.sqrt(at))).signal());
    }

    /** Сила нормирована на цену, иначе бумаги за 100 и за 10000 рублей несравнимы. */
    @Test
    void measuresStrengthAgainstThePrice() {
        Upside cheap = new MacdUpsideCalculator(MacdUpsideCalculator.Source.LINE)
            .calculate(seriesOf(80, at -> 100 * (1 + 0.01 * at)));
        Upside dear = new MacdUpsideCalculator(MacdUpsideCalculator.Source.LINE)
            .calculate(seriesOf(80, at -> 10000 * (1 + 0.01 * at)));

        assertEquals(cheap.strength(), dear.strength(), 1e-9);
        assertTrue(cheap.strength() > 0);
    }

    @Test
    void refusesWhatTheIndicatorRefuses() {
        assertThrows(IllegalArgumentException.class,
            () -> new MacdUpsideCalculator(26, 12, 9, MacdUpsideCalculator.Source.LINE));
        assertThrows(IllegalArgumentException.class,
            () -> new MacdUpsideCalculator(12, 26, 9, null));
    }

    private static List<Candle> rising(int count) {
        return seriesOf(count, at -> 100 + 2.0 * at);
    }

    private static List<Candle> falling(int count) {
        return seriesOf(count, at -> 500 - 2.0 * at);
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
