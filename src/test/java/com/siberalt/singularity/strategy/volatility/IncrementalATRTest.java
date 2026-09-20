package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class IncrementalATRTest {

    @Test
    void saysNothingUntilThePeriodIsFull() {
        IncrementalATR atr = new IncrementalATR(3);
        List<Candle> candles = series(100, 110, 120, 130);

        // Первый бар даёт только закрытие, диапазоны начинаются со второго.
        assertEquals(0, atr.add(candles.get(0)));
        assertEquals(0, atr.add(candles.get(1)));
        assertEquals(0, atr.add(candles.get(2)));
        assertFalse(atr.ready());

        assertTrue(atr.add(candles.get(3)) > 0);
        assertTrue(atr.ready());
    }

    @Test
    void averagesTheFirstRangesAndSmoothsTheRest() {
        // Каждый бар шире предыдущего закрытия ровно на десять.
        IncrementalATR atr = new IncrementalATR(2);
        List<Candle> candles = series(100, 110, 120, 130);

        candles.forEach(atr::add);

        // Первые два диапазона по 10 дают среднее 10, третий тоже 10 - сглаживание его не меняет.
        assertEquals(10, atr.value(), 1e-9);
    }

    /** Смысл всей затеи: то же число, что у прежнего расчёта, но без пересчёта с начала. */
    @Test
    void agreesWithTheCalculatorItReplaces() {
        List<Candle> candles = random(200);

        for (int period : new int[]{2, 14, 50}) {
            IncrementalATR incremental = new IncrementalATR(period);

            candles.forEach(incremental::add);

            assertEquals(new ATRVolatilityCalculator(period).calculate(candles), incremental.value(), 1e-9,
                "период " + period);
        }
    }

    /** Значение после каждого бара - это значение прежнего расчёта по окну, кончающемуся на этом баре. */
    @Test
    void afterEveryBarItHoldsWhatTheWholeWindowWouldHaveGiven() {
        List<Candle> candles = random(60);
        IncrementalATR atr = new IncrementalATR(14);

        for (int at = 0; at < candles.size(); at++) {
            double running = atr.add(candles.get(at));

            assertEquals(new ATRVolatilityCalculator(14).calculate(candles.subList(0, at + 1)), running, 1e-9,
                "бар " + at);
        }
    }

    @Test
    void refusesAPeriodItCannotAverageOver() {
        assertThrows(IllegalArgumentException.class, () -> new IncrementalATR(0));
        assertThrows(IllegalArgumentException.class, () -> new IncrementalATR(-1));
    }

    private static List<Candle> series(double... closes) {
        List<Candle> candles = new ArrayList<>();

        for (int at = 0; at < closes.length; at++) {
            candles.add(Candle.of(new TimePoint(at), 1, closes[at], closes[at], closes[at], closes[at]));
        }

        return candles;
    }

    private static List<Candle> random(int count) {
        List<Candle> candles = new ArrayList<>();
        Random random = new Random(42);
        double close = 100;

        for (int at = 0; at < count; at++) {
            double open = close;
            close = open + random.nextDouble() * 4 - 2;

            candles.add(Candle.of(new TimePoint(at), 1, open,
                Math.max(open, close) + random.nextDouble(), Math.min(open, close) - random.nextDouble(), close));
        }

        return candles;
    }
}
