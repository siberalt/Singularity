package com.siberalt.singularity.strategy.signal.condition;

import com.siberalt.singularity.strategy.signal.FilterSignalSource;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Порог 0.5; крутизна подаётся напрямую. */
class SteepnessGateTest {
    private static final Signal BUY = new Signal(0.95, 1);

    private static final Signal COVER = new Signal(SignalType.POSITION_EXIT, 1, 1, Signal.UNDEFINED_SHARE);

    private static final Signal SELL = new Signal(SignalType.POSITION_EXIT, -1, 1, Signal.UNDEFINED_SHARE);

    private double steepness;

    private final SteepnessGate gate = new SteepnessGate(candles -> steepness, 0.5);

    /** На крутых отрезках покупка не проходит - любая, в том числе закрытие шорта. */
    @Test
    void stopsABuyOnSteepLegs() {
        steepness = 0.8;

        assertFalse(holds(BUY));
        assertFalse(holds(COVER));
    }

    /** Продажа и молчание проходят всегда: закрывать позицию фильтр не мешает. */
    @Test
    void letsASellThroughWhateverTheSlopes() {
        steepness = 5;

        assertTrue(holds(SELL));
        assertTrue(holds(Signal.NEUTRAL));
    }

    /** На пологих и на самом пороге - проходит; не измерить - тоже. */
    @Test
    void letsABuyThroughOnGentleLegs() {
        steepness = 0.3;
        assertTrue(holds(BUY));
        steepness = 0.5;
        assertTrue(holds(BUY));
        steepness = Double.NaN;
        assertTrue(holds(BUY));
    }

    /** Источник спрашивается и на отказанном баре: счёт баров у EntryExit не сбивается. */
    @Test
    void asksTheSignalEvenWhenItRefuses() {
        int[] asked = {0};
        FilterSignalSource filtered = new FilterSignalSource(candles -> {
            asked[0]++;

            return BUY;
        }, gate);

        steepness = 0.8;

        assertSame(Signal.NEUTRAL, filtered.calculate(List.of()));
        assertEquals(1, asked[0]);

        steepness = 0.2;

        assertSame(BUY, filtered.calculate(List.of()));
        assertEquals(2, asked[0]);
    }

    @Test
    void refusesAThresholdItCannotUse() {
        assertThrows(IllegalArgumentException.class, () -> new SteepnessGate(null, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new SteepnessGate(candles -> 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SteepnessGate(candles -> 0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> new SteepnessGate(candles -> 0, Double.POSITIVE_INFINITY));
    }

    private boolean holds(Signal signal) {
        return gate.holds(List.of(), () -> signal);
    }
}
