package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Отрезки по 10 баров с заданными наклонами; волатильность подаётся числом. */
class SlopeSteepnessTest {
    private static final int HALF = 10;

    private final SlopeChangeSignalSource curvature = new SlopeChangeSignalSource(HALF);

    /** Источник отдаёт наклоны своих отрезков - те, из которых сводит сигнал. */
    @Test
    void handsOutTheSlopesTheSignalIsMadeOf() {
        SlopeChangeSignalSource.Slopes slopes = curvature.slopesOf(slopes(2, -1));

        assertEquals(2, slopes.earlier(), 1e-9);
        assertEquals(-1, slopes.later(), 1e-9);
        assertEquals(curvature.calculate(slopes(2, -1)).strength(), slopes.fit(), 1e-12);
        assertNull(curvature.slopesOf(slopes(2, -1).subList(0, 5)));
        assertNull(curvature.slopesOf(List.of()));
    }

    /** Крутизна - наибольший из двух наклонов в волатильностях; знак не важен. */
    @Test
    void readsTheSteeperLegInVolatilities() {
        assertEquals(1, new SlopeSteepness(curvature, candles -> 2).of(slopes(2, -1)), 1e-9);
        assertEquals(1.5, new SlopeSteepness(curvature, candles -> 2).of(slopes(-1, 3)), 1e-9);
    }

    /** Мерить нечего - NaN: источник молчит или волатильность не положительна. */
    @Test
    void saysNothingItCannotMeasure() {
        assertTrue(Double.isNaN(new SlopeSteepness(curvature, candles -> 2).of(slopes(2, -1).subList(0, 5))));
        assertTrue(Double.isNaN(new SlopeSteepness(curvature, candles -> 0).of(slopes(2, -1))));
    }

    @Test
    void needsBothParts() {
        assertThrows(IllegalArgumentException.class, () -> new SlopeSteepness(null, candles -> 1));
        assertThrows(IllegalArgumentException.class, () -> new SlopeSteepness(curvature, null));
    }

    private static List<Candle> slopes(double earlier, double later) {
        List<Candle> candles = new ArrayList<>();
        double price = 1000;

        for (int at = 0; at < 2 * HALF; at++) {
            Quotation value = Quotation.of(price);

            candles.add(new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
                value, value, value, value, 1));
            price += at < HALF ? earlier : later;
        }

        return candles;
    }
}
