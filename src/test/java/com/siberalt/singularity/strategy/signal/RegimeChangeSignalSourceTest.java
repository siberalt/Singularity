package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Режим подаётся по сценарию, бар за баром. */
class RegimeChangeSignalSourceTest {
    private double regime;

    private final RegimeChangeSignalSource source = new RegimeChangeSignalSource(candles -> regime);

    /** Первая увиденная сторона - не смена: закрывать нечего. Пока сторона держится - тоже тишина. */
    @Test
    void remembersTheFirstSideSilently() {
        assertSame(Signal.NEUTRAL, at(Double.NaN));
        assertSame(Signal.NEUTRAL, at(1));
        assertSame(Signal.NEUTRAL, at(1));
    }

    /** Смена стороны закрывает старую: выход против неё на весь счёт. */
    @Test
    void closesTheOldSideOnAChange() {
        at(1);

        Signal close = at(-1);

        assertEquals(SignalType.POSITION_EXIT, close.type());
        assertEquals(-1, close.confidence());
        assertEquals(1, close.share());
        assertEquals(1, at(1).confidence(), "смена вниз-вверх закрывает шорт");
    }

    /** Потеря режима - тоже смена: держать позицию больше не на чем. Возврат стороны после неё - не смена. */
    @Test
    void closesWhenTheRegimeIsLost() {
        at(1);

        assertEquals(-1, at(Double.NaN).confidence());
        assertSame(Signal.NEUTRAL, at(Double.NaN));
        assertSame(Signal.NEUTRAL, at(1));
        assertEquals(-1, at(0).confidence(), "ноль - тоже «не измерить»");
    }

    @Test
    void readsOnlyTheSignOfTheRegime() {
        at(0.3);

        assertSame(Signal.NEUTRAL, at(2));
        assertEquals(-1, at(-0.1).confidence());
    }

    @Test
    void needsARegimeToWatch() {
        assertThrows(IllegalArgumentException.class, () -> new RegimeChangeSignalSource(null));
    }

    private Signal at(double reading) {
        regime = reading;

        return source.calculate(List.of());
    }
}
