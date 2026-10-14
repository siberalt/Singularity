package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Односторонность хода кривой: предел означает, что кривая не изгибалась ни разу. */
class TrendAngleTest {
    private static final int PERIOD = 5;
    private static final int BARS = 10;

    /** Средняя шла вверх на каждом баре - это и есть предел шкалы. */
    @Test
    void readsAStraightRiseAsTheLimit() {
        assertEquals(1, TrendAngle.overSma(PERIOD, BARS).of(series(100, 1)), 1e-12);
    }

    @Test
    void readsAStraightFallAsTheLimit() {
        assertEquals(-1, TrendAngle.overSma(PERIOD, BARS).of(series(100, -1)), 1e-12);
    }

    /**
     * Ход туда и обратно даёт <b>ровно</b> ноль, а не «около». Средняя за пять по подъёму 100..107 и
     * такому же спуску приходит туда, откуда ушла: {@code (100+101+102+103+104)/5 = 102} в начале окна
     * и {@code (104+103+102+101+100)/5 = 102} в конце. Чистый ход нулевой, путь нет.
     */
    @Test
    void readsAWanderThatCameBackAsFlat() {
        assertEquals(0, TrendAngle.overSma(PERIOD, BARS).of(thereAndBack()), 1e-12);
    }

    /** Неподвижной величине направления не придумать, и ноль для неё - правильный ответ. */
    @Test
    void readsAMotionlessSeriesAsZero() {
        assertEquals(0, TrendAngle.overSma(PERIOD, BARS).of(series(100, 0)), 1e-12);
    }

    /**
     * Кривая здесь любая, а не только средняя - в том и смысл отделения ряда от меры. Ряд, не имеющий
     * к ценам никакого отношения, читается той же формулой.
     */
    @Test
    void readsAnySeriesNotJustAnAverage() {
        MarketSeries squares = candles -> {
            double[] readings = new double[candles.size()];

            for (int at = 0; at < readings.length; at++) {
                readings[at] = at * at;
            }

            return readings;
        };

        assertEquals(1, new TrendAngle(squares, BARS).of(series(100, 0)), 1e-12,
            "ряд растёт на каждом баре, какой бы ни была цена");
    }

    /**
     * Арифметика целиком на виду, без оглядки на другие классы. Средняя из одной цены - это цена, и
     * на дёрганом подъёме её приращения чередуются: {@code -0.6} и {@code +2.6}, по пять каждого в
     * окне. Чистый ход {@code 5·2.6 − 5·0.6 = 10}, путь {@code 5·2.6 + 5·0.6 = 16}, тангенс
     * {@code 0.625}, то есть {@code 32.0°} из предельных {@code 45°}.
     */
    @Test
    void readsANoisyRiseByItsOwnArithmetic() {
        assertEquals(0.7112, TrendAngle.overSma(1, BARS).of(noisyRise()), 1e-4);
    }

    /**
     * И вот зачем кривая: на том же ряду, где цена разворачивается через бар, средняя за пять не
     * изгибается ни разу - её приращение {@code (pᵢ − pᵢ₋₅)/5} положительно при любой чётности, -
     * и угол упирается в предел. Ровно та фильтрация значимых изгибов, за которой класс и нужен.
     */
    @Test
    void ironsOutABendThatTurnsThePriceBackward() {
        List<Candle> noisy = noisyRise();

        assertEquals(1, TrendAngle.overSma(PERIOD, BARS).of(noisy), 1e-12, "средняя - предел");
        assertTrue(TrendAngle.overSma(1, BARS).of(noisy) < 1, "цена предела не достигает");
    }

    /** Та же мера по экспоненциальной средней - другая кривая, и шкала у неё своя. */
    @Test
    void readsAnExponentialAverageToo() {
        assertEquals(1, TrendAngle.overEma(PERIOD, BARS).of(series(100, 1)), 1e-12);
    }

    /**
     * Прогрев кривой - дело самой кривой: {@code TrendAngle} видит NaN в своём окне и отвечает NaN,
     * а арифметику периода средней у себя не повторяет.
     */
    @Test
    void saysNothingWhileTheSeriesHasNotWarmedUp() {
        TrendAngle angle = TrendAngle.overSma(PERIOD, BARS);
        List<Candle> rising = series(100, 1);

        assertTrue(Double.isNaN(angle.of(rising.subList(0, PERIOD + BARS - 1))),
            "на одну свечу меньше - ответа нет");
        assertFalse(Double.isNaN(angle.of(rising.subList(0, PERIOD + BARS))), "ровно столько - ответ есть");
        assertTrue(Double.isNaN(angle.of(null)));
        assertTrue(Double.isNaN(angle.of(rising.subList(0, BARS))), "приращений не хватает вовсе");
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new TrendAngle(null, BARS));
        assertThrows(IllegalArgumentException.class,
            () -> new TrendAngle(candles -> new double[0], 0));
        assertThrows(IllegalArgumentException.class, () -> TrendAngle.overSma(0, BARS));
        assertThrows(IllegalArgumentException.class, () -> TrendAngle.overEma(PERIOD, BARS, null));
    }

    /** Ровный ход с постоянным шагом. */
    private static List<Candle> series(double from, double step) {
        List<Candle> candles = new ArrayList<>();

        for (int at = 0; at < PERIOD + BARS; at++) {
            candles.add(candle(at, from + step * at));
        }

        return candles;
    }

    /** Подъём и такой же спуск: средняя изгибается один раз, и чистый ход съедается. */
    private static List<Candle> thereAndBack() {
        List<Candle> candles = new ArrayList<>();
        int half = (PERIOD + BARS) / 2;

        for (int at = 0; at < PERIOD + BARS; at++) {
            candles.add(candle(at, 100 + (at <= half ? at : 2 * half - at)));
        }

        return candles;
    }

    /** Подъём с дёрганьем: каждый второй бар отступает назад, но ряд всё равно идёт вверх. */
    private static List<Candle> noisyRise() {
        List<Candle> candles = new ArrayList<>();

        for (int at = 0; at < PERIOD + BARS; at++) {
            candles.add(candle(at, 100 + at + (at % 2 == 0 ? 0 : -1.6)));
        }

        return candles;
    }

    private static Candle candle(int at, double price) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, 1);
    }
}
