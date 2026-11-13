package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.strategy.market.MarketCoefficient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Нижний порог 0.5, откат до 1.5; расстояние подаётся напрямую. */
class DistanceReentryTest {
    private static final TakeProgress LONG = new TakeProgress(1, 0.1, 1);

    private static final TakeProgress SHORT = new TakeProgress(1, 0.1, -1);

    private double distance;

    private final DistanceReentry rule = new DistanceReentry(candles -> distance, 0.5, 1.5);

    /** Пока план не выполнен - только на откате, между нижним порогом и откатом. */
    @Test
    void buysBackOnAPullbackWhileThePlanRuns() {
        assertTrue(allows(LONG, false, 0.5));
        assertTrue(allows(LONG, false, 1.5));
        assertFalse(allows(LONG, false, 2.5), "отката нет");
        assertFalse(allows(LONG, false, 0.3), "у самой средней");
    }

    /** План выполнен - запасной вход: и без отката, но не у средней. */
    @Test
    void buysBackWithoutAPullbackOnceThePlanIsDone() {
        assertTrue(allows(LONG, true, 2.5));
        assertTrue(allows(LONG, true, 0.8));
        assertFalse(allows(LONG, true, 0.3));
        assertFalse(allows(LONG, true, -1));
    }

    @Test
    void readsAShortFromBelow() {
        assertTrue(allows(SHORT, false, -1));
        assertFalse(allows(SHORT, false, 1));
        assertTrue(allows(SHORT, true, -3));
    }

    @Test
    void doesNotBuyBackWhatItCannotMeasure() {
        assertFalse(allows(LONG, false, Double.NaN));
        assertFalse(allows(LONG, true, Double.NaN));
    }

    @Test
    void refusesARuleItCannotKeep() {
        MarketCoefficient any = candles -> 0;

        assertThrows(IllegalArgumentException.class, () -> new DistanceReentry(null, 0.5, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceReentry(any, -0.1, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceReentry(any, 2, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceReentry(any, 0.5, Double.NaN));
    }

    private boolean allows(TakeProgress progress, boolean done, double distance) {
        this.distance = distance;

        return rule.allows(progress, done, List.of());
    }
}
