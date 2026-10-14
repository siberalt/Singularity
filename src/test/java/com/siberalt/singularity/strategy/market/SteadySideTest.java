package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сторона с двумя порогами: объявить дорого, удержать дешевле. */
class SteadySideTest {
    private static final double DECISIVE = 0.6;
    private static final double REMAINS = 0.25;

    private final double[] reading = {0};

    /** Нерешительного чтения не хватает: тренда нет, пока он не заявлен. */
    @Test
    void declaresASideOnlyOnADecisiveReading() {
        SteadySide side = steady(REMAINS);

        reading[0] = 0.4;
        assertEquals(SteadySide.FLAT, side.of(bars()));

        reading[0] = 0.7;
        assertEquals(SteadySide.UP, side.of(bars()));
    }

    /** Слабое согласное чтение сторону держит - за это два порога и заведены. */
    @Test
    void keepsTheSideThroughAWeakAgreeingReading() {
        SteadySide side = upThen(0.3);

        assertEquals(SteadySide.UP, side.of(bars()));
    }

    /**
     * Слабый изгиб против тренда - не другой тренд, а отсутствие тренда. Именно здесь гистерезис
     * отличается от простого знака: знак объявил бы разворот.
     */
    @Test
    void dropsToFlatRatherThanFlippingOnAWeakContraryReading() {
        assertEquals(SteadySide.FLAT, upThen(-0.3).of(bars()));
    }

    /** Выдохшийся ход - тоже флэт, хотя сторона чтения не менялась. */
    @Test
    void dropsToFlatWhenTheMoveFades() {
        assertEquals(SteadySide.FLAT, upThen(0.1).of(bars()));
    }

    /** Решительное обратное чтение переворачивает сторону сразу, без остановки во флэте. */
    @Test
    void flipsStraightOverOnADecisiveContraryReading() {
        assertEquals(SteadySide.DOWN, upThen(-0.8).of(bars()));
    }

    /**
     * При сошедшихся порогах это обычное переключение - контроль, с которым гистерезис и надо
     * сравнивать: одна настройка разницы, а не другая конструкция.
     */
    @Test
    void becomesAPlainSwitchWhenTheThresholdsMeet() {
        SteadySide switching = steady(DECISIVE);

        reading[0] = 0.7;
        assertEquals(SteadySide.UP, switching.of(bars()));

        reading[0] = 0.5;
        assertEquals(SteadySide.FLAT, switching.of(bars()), "без зоны удержания сторона не держится");
    }

    /** Отсутствие чтения - не повод объявить флэт: сторона переживает прогрев. */
    @Test
    void keepsTheSideWhileThereIsNoReading() {
        SteadySide side = upThen(Double.NaN);

        assertTrue(Double.isNaN(side.of(bars())), "нет чтения - нет ответа");
        assertEquals(SteadySide.UP, side.side(), "но сторона помнится");

        reading[0] = 0.3;
        assertEquals(SteadySide.UP, side.of(bars()));
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new SteadySide(null, DECISIVE, REMAINS));
        assertThrows(IllegalArgumentException.class, () -> new SteadySide(candles -> 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SteadySide(candles -> 0, 1.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new SteadySide(candles -> 0, 0.5, -0.1));
        assertThrows(IllegalArgumentException.class,
            () -> new SteadySide(candles -> 0, 0.5, 0.6));
    }

    /** Сторона объявлена решительным ростом, после чего чтение становится этим. */
    private SteadySide upThen(double next) {
        SteadySide side = steady(REMAINS);

        reading[0] = 0.9;
        side.of(bars());
        reading[0] = next;

        return side;
    }

    private SteadySide steady(double remains) {
        return new SteadySide(candles -> reading[0], DECISIVE, remains);
    }

    /** Свечи здесь не читаются: сторона определяется чтением коэффициента, а не ими. */
    private static List<Candle> bars() {
        return List.of();
    }
}
