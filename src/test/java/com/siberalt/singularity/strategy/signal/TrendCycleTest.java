package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import static com.siberalt.singularity.strategy.market.TrendZone.DOWN;
import static com.siberalt.singularity.strategy.market.TrendZone.UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrendCycleTest {
    private static final double THRESHOLD = 0.3;

    private static final double UNKNOWN = Double.NaN;

    private final TrendCycle cycle = new TrendCycle(THRESHOLD, true);

    /** Тренд есть, но нога пологая: ждём, а не входим на первом часе. */
    @Test
    void waitsForTheLegToConfirmTheTrend() {
        assertEquals(0, cycle.next(UP, 0.1));
        assertEquals(0, cycle.next(UP, 0.3));
        assertEquals(1, cycle.next(UP, 0.31));
    }

    /** Нога против тренда - не подтверждение, как бы круто она ни шла. */
    @Test
    void wantsTheLegInTheTrendsDirection() {
        assertEquals(0, cycle.next(UP, -0.9));
        assertEquals(0, cycle.next(DOWN, 0.9));
        assertEquals(-1, cycle.next(DOWN, -0.9));
    }

    /** Вошли - держим до смены тренда: наклон дальше ничего не решает. */
    @Test
    void holdsUntilTheTrendChanges() {
        cycle.next(UP, 0.5);

        assertEquals(1, cycle.next(UP, -0.9));
        assertEquals(1, cycle.next(UP, Double.NaN));
    }

    /**
     * Смена тренда закрывает позицию сразу, а в новую сторону входит снова по порогу: из лонга не в
     * шорт, а в деньги, пока нисходящая нога не подтвердит.
     */
    @Test
    void closesOnATrendChangeAndWaitsAgain() {
        cycle.next(UP, 0.5);

        assertEquals(0, cycle.next(DOWN, -0.1));
        assertEquals(-1, cycle.next(DOWN, -0.5));
    }

    /** Если на смене нога уже идёт круто в новую сторону, разворот - на том же баре. */
    @Test
    void reversesAtOnceWhenTheLegAlreadyConfirms() {
        cycle.next(UP, 0.5);

        assertEquals(-1, cycle.next(DOWN, -0.5));
    }

    /** Тренд потерян - позиция закрыта; вернулся тот же - снова ждём подтверждения. */
    @Test
    void treatsAReturningTrendAsANewOne() {
        cycle.next(UP, 0.5);

        assertEquals(0, cycle.next(UNKNOWN, 0.9));
        assertEquals(0, cycle.next(UP, 0.1));
        assertEquals(1, cycle.next(UP, 0.5));
    }

    @Test
    void neverShortsWhenShortsAreOff() {
        TrendCycle longOnly = new TrendCycle(THRESHOLD, false);

        longOnly.next(UP, 0.5);

        assertEquals(0, longOnly.next(DOWN, -0.9));
        assertEquals(0, longOnly.next(DOWN, -0.9));
    }

    @Test
    void entersNothingWithoutAMeasuredLeg() {
        assertEquals(0, cycle.next(UP, Double.NaN));
    }

    /** Контроль: вход на первом же баре тренда, наклон не спрашивается вовсе. */
    @Test
    void immediateEntersOnTheFirstBarOfATrend() {
        TrendCycle immediate = TrendCycle.immediate(true);

        assertEquals(1, immediate.next(UP, Double.NaN));
        assertEquals(-1, immediate.next(DOWN, 0.9));
        assertEquals(0, immediate.next(UNKNOWN, -0.9));
        assertEquals(0, TrendCycle.immediate(false).next(DOWN, -0.9));
    }

    @Test
    void startsInMoney() {
        assertEquals(0, cycle.target());
    }

    @Test
    void refusesWhatItCannotRead() {
        assertThrows(IllegalArgumentException.class, () -> new TrendCycle(Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> new TrendCycle(1, true));
        assertThrows(IllegalArgumentException.class, () -> cycle.next(0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> cycle.next(0, 0.5));
    }
}
