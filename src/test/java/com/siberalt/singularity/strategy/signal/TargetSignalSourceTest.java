package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Цель подаётся по сценарию, бар за баром; свечи источнику не нужны. */
class TargetSignalSourceTest {
    private final List<Double> targets = new ArrayList<>();

    private int bar;

    private final TargetSignalSource source = new TargetSignalSource(candles -> targets.get(bar));

    @Test
    void saysNothingUntilTheTargetFirstMoves() {
        assertSame(Signal.NEUTRAL, next(0));
    }

    @Test
    void buysTheWholeChangeOfTheTarget() {
        Signal entry = next(1);

        assertEquals(1, entry.confidence());
        assertEquals(1, entry.share());
        assertEquals(1, entry.strength());
        assertEquals(SignalType.POSITION_ENTRY, entry.type());
    }

    /** Доля - размер сделки: цель держится - сделки нет, иначе та же доля купилась бы ещё раз. */
    @Test
    void saysNothingWhileTheTargetHolds() {
        next(1);

        assertSame(Signal.NEUTRAL, next(1));
    }

    @Test
    void sellsToMoneyAsAnExit() {
        next(1);

        Signal exit = next(0);

        assertEquals(-1, exit.confidence());
        assertEquals(1, exit.share());
        assertEquals(SignalType.POSITION_EXIT, exit.type());
    }

    /** Частичная фиксация - тоже выход: продажа, а позиция остаётся лонгом. */
    @Test
    void sellsAPartAsAnExit() {
        next(1);

        Signal part = next(0.88);

        assertEquals(-1, part.confidence());
        assertEquals(0.12, part.share(), 1e-12);
        assertEquals(0.12, part.strength(), 1e-12);
        assertEquals(SignalType.POSITION_EXIT, part.type());
    }

    /** Разворот в шорт одним баром - продажа на два счёта, и это вход: позиция сменила сторону. */
    @Test
    void sellsIntoAShortAsAnEntry() {
        next(1);

        Signal reversal = next(-1);

        assertEquals(-1, reversal.confidence());
        assertEquals(2, reversal.share());
        assertEquals(SignalType.POSITION_ENTRY, reversal.type());
    }

    @Test
    void buysBackTheShortAsAnExit() {
        next(-1);

        Signal cover = next(0);

        assertEquals(1, cover.confidence());
        assertEquals(1, cover.share());
        assertEquals(SignalType.POSITION_EXIT, cover.type());
    }

    @Test
    void refusesATargetOutsideTheAccount() {
        assertThrows(IllegalArgumentException.class, () -> next(1.5));
        assertThrows(IllegalArgumentException.class,
            () -> new TargetSignalSource(candles -> Double.NaN).calculate(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TargetSignalSource(null));
    }

    private Signal next(double target) {
        targets.add(target);

        Signal signal = source.calculate(List.<Candle>of());

        bar++;

        return signal;
    }
}
