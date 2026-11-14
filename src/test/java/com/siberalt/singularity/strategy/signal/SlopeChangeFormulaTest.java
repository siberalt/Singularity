package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Две формулы на одних и тех же парах наклонов - те, что в таблице javadoc. */
class SlopeChangeFormulaTest {
    private static final SlopeChangeFormula SUM = SlopeChangeFormula.SUM;

    private static final SlopeChangeFormula MIRROR = SlopeChangeFormula.MIRROR;

    /** Зеркало - предел у обеих; ровный тренд и неподвижность - ноль у обеих. */
    @Test
    void agreeOnTheMirrorAndOnASteadyTrend() {
        assertEquals(-1, SUM.of(1, -1), 1e-12);
        assertEquals(-1, MIRROR.of(1, -1), 1e-12);
        assertEquals(1, MIRROR.of(-2, 2), 1e-12);
        assertEquals(0, SUM.of(2, 2));
        assertEquals(0, MIRROR.of(2, 2));
        assertEquals(0, MIRROR.of(0, 0));
    }

    /** Сумма модулей насыщается на любой смене знака, зеркальная - нет. */
    @Test
    void differOnALopsidedReversal() {
        assertEquals(-1, SUM.of(1, -0.1), 1e-12);
        assertEquals(-0.55, MIRROR.of(1, -0.1), 1e-12);
        assertEquals(-1, SUM.of(0.1, -3), 1e-12);
        assertEquals(-3.1 / 6, MIRROR.of(0.1, -3), 1e-12);
        assertEquals(-1, SUM.of(1, 0), 1e-12);
        assertEquals(-0.5, MIRROR.of(1, 0), 1e-12);
    }

    /** Замедление и ускорение остаются - у зеркальной вполсилы. */
    @Test
    void keepSlowingAndSpeedingUpAtHalfStrength() {
        assertEquals(-0.45, MIRROR.of(1, 0.1), 1e-12);
        assertEquals(2.9 / 6, MIRROR.of(0.1, 3), 1e-12);
        assertEquals(0.5, MIRROR.of(-1, 0), 1e-12);
    }

    /** Однородна нулевой степени и не выходит из [−1, 1]. */
    @Test
    void ignoresScaleAndStaysInRange() {
        assertEquals(MIRROR.of(0.3, -0.7), MIRROR.of(300, -700), 1e-12);

        for (double earlier = -3; earlier <= 3; earlier += 0.25) {
            for (double later = -3; later <= 3; later += 0.25) {
                double value = MIRROR.of(earlier, later);

                assertTrue(value >= -1 && value <= 1, earlier + " → " + later + ": " + value);
                assertTrue(Math.abs(value) <= Math.abs(SUM.of(earlier, later)) + 1e-12,
                    "зеркальная не сильнее суммы модулей");
            }
        }
    }
}
