package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siberalt.singularity.strategy.market.TrendZone.DOWN;
import static com.siberalt.singularity.strategy.market.TrendZone.UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Чтения подаются по сценарию, бар за баром; свечи цели не нужны. */
class TrendCycleTargetTest {
    private int bar;

    @Test
    void feedsTheTrendAndTheLegToTheCycle() {
        List<Double> trends = List.of(UP, UP, DOWN);
        List<Double> slopes = List.of(0.1, 0.5, -0.5);
        TrendCycleTarget target = new TrendCycleTarget(
            candles -> trends.get(bar),
            candles -> new Signal(slopes.get(bar), 1),
            new TrendCycle(0.3, true)
        );

        assertEquals(0, next(target));
        assertEquals(1, next(target));
        assertEquals(-1, next(target));
    }

    @Test
    void refusesMissingParts() {
        TrendCycle cycle = new TrendCycle(0.3, true);

        assertThrows(IllegalArgumentException.class,
            () -> new TrendCycleTarget(null, candles -> Signal.NEUTRAL, cycle));
        assertThrows(IllegalArgumentException.class, () -> new TrendCycleTarget(candles -> UP, null, cycle));
        assertThrows(IllegalArgumentException.class,
            () -> new TrendCycleTarget(candles -> UP, candles -> Signal.NEUTRAL, null));
    }

    private double next(TrendCycleTarget target) {
        double value = target.of(List.of());

        bar++;

        return value;
    }
}
