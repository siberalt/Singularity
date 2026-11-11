package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.strategy.market.MarketCoefficient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Доля 0.12 на полном расстоянии 5, всего до 0.30; расстояние подаётся напрямую. */
class DistanceTakePlanTest {
    private double distance;

    private final DistanceTakePlan plan = new DistanceTakePlan(candles -> distance, 0.12, 0.30, 5);

    /** Доля растёт с расстоянием в сторону позиции и дальше полного не растёт. */
    @Test
    void takesMoreTheFurtherThePriceWent() {
        assertEquals(0.06, shareAt(2.5, 1), 1e-12);
        assertEquals(0.12, shareAt(5, 1), 1e-12);
        assertEquals(0.12, shareAt(10, 1), 1e-12);
    }

    /** У средней и за ней снимать нечего; не измерить - тоже ноль. */
    @Test
    void takesNothingAtTheAverageOrBehindIt() {
        assertEquals(0, shareAt(0, 1));
        assertEquals(0, shareAt(-3, 1));
        assertEquals(0, shareAt(Double.NaN, 1));
    }

    /** У шорта «в сторону позиции» - ниже средней. */
    @Test
    void readsAShortFromBelow() {
        assertEquals(0.12, shareAt(-5, -1), 1e-12);
        assertEquals(0, shareAt(5, -1));
    }

    /** Последняя фиксация урезается до остатка, и на нём план выполнен. */
    @Test
    void neverTakesMoreThanTheCap() {
        distance = 5;

        assertEquals(0.05, plan.shareOf(2, 0.25, 1, List.of()), 1e-12);
        assertFalse(plan.done(2, 0.25));
        assertTrue(plan.done(3, 0.30));
    }

    /** Контроль: полное расстояние ноль - доля одна и та же, расстояние не спрашивается. */
    @Test
    void takesTheSameShareWhenTheDistanceDoesNotMatter() {
        MarketCoefficient unread = candles -> {
            throw new AssertionError("расстояние не нужно");
        };
        DistanceTakePlan constant = new DistanceTakePlan(unread, 0.05, 0.30, 0);

        assertEquals(0.05, constant.shareOf(0, 0, 1, List.of()));
        assertEquals(0.05, constant.shareOf(0, 0, -1, List.of()));
    }

    @Test
    void refusesAPlanItCannotKeep() {
        MarketCoefficient any = candles -> 0;

        assertThrows(IllegalArgumentException.class, () -> new DistanceTakePlan(null, 0.1, 0.3, 5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceTakePlan(any, 0, 0.3, 5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceTakePlan(any, 0.4, 0.3, 5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceTakePlan(any, 0.1, 1.5, 5));
        assertThrows(IllegalArgumentException.class, () -> new DistanceTakePlan(any, 0.1, 0.3, -1));
        assertThrows(IllegalArgumentException.class,
            () -> new DistanceTakePlan(any, 0.1, 0.3, Double.POSITIVE_INFINITY));
    }

    private double shareAt(double distance, double side) {
        this.distance = distance;

        return plan.shareOf(0, 0, side, List.of());
    }
}
