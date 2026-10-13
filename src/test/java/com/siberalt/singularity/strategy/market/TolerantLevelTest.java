package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.signal.ChangeAngleSignalSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Уровень, опущенный на столько, насколько ход через него нерешителен. */
class TolerantLevelTest {
    private static final int BARS = 10;
    private static final double LEVEL = 100;

    /** Резкому ходу терпимости нет: порог выхода остаётся на самом уровне. */
    @Test
    void givesNoGroundToADecisiveFall() {
        assertEquals(LEVEL, tolerant(0.05, 0.5).of(falling(2)), 1e-9);
    }

    /** Нерешительному дана зона: порог опускается на терпимость ниже уровня. */
    @Test
    void givesGroundToADrift() {
        assertEquals(LEVEL * 0.95, tolerant(0.05, 0.5).of(drifting()), 1e-9);
    }

    /** Рост терпимости не отменяет - опускать порог на подъёме незачем. */
    @Test
    void keepsTheGroundWhilePriceRises() {
        assertEquals(LEVEL * 0.95, tolerant(0.05, 0.5).of(rising(2)), 1e-9);
    }

    /**
     * Резкость и значимость - разные вещи: самый резкий пробой в этой базе был ходом выходной площадки и
     * не подтвердился. С требованием к объёму терпимость снимается только с падения, за которым он стоит.
     */
    @Test
    void demandsParticipationBeforeWithdrawingTheGroundWhenAsked() {
        MarketCoefficient needsVolume = new TolerantLevel(candles -> LEVEL,
            new ChangeAngleSignalSource(BARS), 0.05, 0.5, 0.9);

        // Падение резкое, но объём падает вместе с ценой - терпимость остаётся.
        assertEquals(LEVEL * 0.95, needsVolume.of(fallingOnFadingVolume()), 1e-9);
        // То же падение на растущем объёме терпимость снимает.
        assertEquals(LEVEL, needsVolume.of(fallingOnRisingVolume()), 1e-9);
    }

    /** Нет уровня - нет и ответа: домножать NaN на терпимость незачем. */
    @Test
    void saysNothingWithoutALevel() {
        assertTrue(Double.isNaN(new TolerantLevel(candles -> Double.NaN,
            new ChangeAngleSignalSource(BARS), 0.05, 0.5).of(drifting())));
    }

    @Test
    void refusesWhatCannotBeComputed() {
        ChangeAngleSignalSource angle = new ChangeAngleSignalSource(BARS);

        assertThrows(IllegalArgumentException.class,
            () -> new TolerantLevel(null, angle, 0.05, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new TolerantLevel(candles -> LEVEL, null, 0.05, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new TolerantLevel(candles -> LEVEL, angle, 0, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new TolerantLevel(candles -> LEVEL, angle, 1, 0.5));
        assertThrows(IllegalArgumentException.class,
            () -> new TolerantLevel(candles -> LEVEL, angle, 0.05, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new TolerantLevel(candles -> LEVEL, angle, 0.05, 0.5, 1.5));
    }

    private static MarketCoefficient tolerant(double maxTolerance, double steepEnough) {
        return new TolerantLevel(candles -> LEVEL, new ChangeAngleSignalSource(BARS),
            maxTolerance, steepEnough);
    }

    /** Все бары в одну сторону - предельный угол, то есть уверенность −1. */
    private static List<Candle> falling(double step) {
        return series(120, -step, 1, 0);
    }

    private static List<Candle> rising(double step) {
        return series(80, step, 1, 0);
    }

    /** Ход туда и обратно: угол сходится в ничью, то есть решительности нет. */
    private static List<Candle> drifting() {
        List<Candle> candles = new ArrayList<>(BARS + 1);

        for (int at = 0; at <= BARS; at++) {
            candles.add(candle(at, 100 + (at % 2 == 0 ? 0 : 2), 1));
        }

        return candles;
    }

    private static List<Candle> fallingOnFadingVolume() {
        return series(120, -2, 100, -8);
    }

    private static List<Candle> fallingOnRisingVolume() {
        return series(120, -2, 10, 8);
    }

    private static List<Candle> series(double from, double step, long volume, long volumeStep) {
        List<Candle> candles = new ArrayList<>(BARS + 1);

        for (int at = 0; at <= BARS; at++) {
            candles.add(candle(at, from + step * at, volume + volumeStep * at));
        }

        return candles;
    }

    private static Candle candle(int at, double price, long volume) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, volume);
    }
}
