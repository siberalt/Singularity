package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Сигналы источника подаются по сценарию, бар за баром; пороги 0.5 и 0.8, план 0.1, 0.2, 0.2. */
class PartialExitSignalSourceTest {
    private static final Signal BUY = new Signal(SignalType.POSITION_ENTRY, 1, 0.3, Signal.UNDEFINED_SHARE);

    private static final Signal SHORT = new Signal(SignalType.POSITION_ENTRY, -1, 0.3, Signal.UNDEFINED_SHARE);

    private static final Signal SELL = new Signal(SignalType.POSITION_EXIT, -0.9, 0.4, Signal.UNDEFINED_SHARE);

    private static final Signal COVER = new Signal(SignalType.POSITION_EXIT, 0.9, 0.4, Signal.UNDEFINED_SHARE);

    private final List<Signal> signals = new ArrayList<>();

    private int bar;

    private final PartialExitSignalSource source =
        new PartialExitSignalSource(candles -> signals.get(bar), 0.5, 0.8, 0.1, 0.2, 0.2);

    /** До входа источник молчит обо всём, кроме входа: выходить не из чего. */
    @Test
    void waitsForAnEntry() {
        assertSame(Signal.NEUTRAL, next(SELL));
        assertSame(Signal.NEUTRAL, next(new Signal(SignalType.POSITION_ENTRY, 0.4, 1, Signal.UNDEFINED_SHARE)));
        assertSame(BUY, next(BUY));
    }

    /** Встречный выход после входа - фиксация: тип другой, уверенность полная, доля - шаг плана. */
    @Test
    void turnsAnExitIntoATakeProfit() {
        next(BUY);

        Signal fixation = next(SELL);

        assertEquals(SignalType.TAKE_PROFIT, fixation.type());
        assertEquals(-1, fixation.confidence());
        assertEquals(0.1, fixation.share());
        assertEquals(0.4, fixation.strength());
    }

    /** Шаги идут по плану, а между ними - тишина, в том числе на новых входах. */
    @Test
    void walksThePlanStepByStep() {
        next(BUY);

        assertEquals(0.1, next(SELL).share());
        assertSame(Signal.NEUTRAL, next(BUY));
        assertSame(Signal.NEUTRAL, next(Signal.NEUTRAL));
        assertEquals(0.2, next(SELL).share());
        assertEquals(0.2, next(SELL).share());
    }

    /** План выполнен - снова ждёт вход: следующий выход не фиксирует, следующий вход проходит. */
    @Test
    void startsOverOnceThePlanIsDone() {
        next(BUY);
        next(SELL);
        next(SELL);
        next(SELL);

        assertSame(Signal.NEUTRAL, next(SELL));
        assertSame(BUY, next(BUY));
        assertEquals(0.1, next(SELL).share());
    }

    /** Фиксация - только против позиции, только выходом и только по своему порогу. */
    @Test
    void catchesOnlyStrongExitsAgainstThePosition() {
        next(BUY);

        assertSame(Signal.NEUTRAL, next(COVER));
        assertSame(Signal.NEUTRAL, next(new Signal(SignalType.POSITION_EXIT, -0.7, 1, Signal.UNDEFINED_SHARE)));
        assertSame(Signal.NEUTRAL, next(new Signal(SignalType.POSITION_ENTRY, -1, 1, Signal.UNDEFINED_SHARE)));
        assertEquals(SignalType.TAKE_PROFIT, next(SELL).type());
    }

    /** В шорте фиксация - откуп. */
    @Test
    void buysBackAPartOfAShort() {
        next(SHORT);

        Signal fixation = next(COVER);

        assertEquals(1, fixation.confidence());
        assertEquals(0.1, fixation.share());
    }

    /** Источнику, который знает только направление, роль задаёт фиксер: сначала вход, потом выход. */
    @Test
    void readsBareDirectionAsEitherRole() {
        Signal up = new Signal(1, 0.5);
        Signal down = new Signal(-1, 0.5);

        assertSame(up, next(up));
        assertSame(Signal.NEUTRAL, next(up));
        assertEquals(SignalType.TAKE_PROFIT, next(down).type());
    }

    @Test
    void refusesAPlanItCannotKeep() {
        SignalSource any = candles -> Signal.NEUTRAL;

        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(null, 0.5, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0.5, 1.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0.5, 0.5, 0.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0.5, 0.5, 0.6, 0.6));
    }

    private Signal next(Signal signal) {
        signals.add(signal);

        Signal result = source.calculate(List.of());

        bar++;

        return result;
    }
}
