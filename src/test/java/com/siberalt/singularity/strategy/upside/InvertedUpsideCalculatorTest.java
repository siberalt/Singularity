package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvertedUpsideCalculatorTest {
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        "TEST",
        new TimePoint(Instant.parse("2021-06-03T10:00:00Z")),
        Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    @Test
    void betsTheOtherWay() {
        assertEquals(-0.8, inverting(0.8, 1).calculate(ANY_CANDLES).signal(), 1e-9);
        assertEquals(0.8, inverting(-0.8, 1).calculate(ANY_CANDLES).signal(), 1e-9);
    }

    /** Turning a signal round says nothing about how sure it was. */
    @Test
    void keepsTheStrengthUntouched() {
        assertEquals(0.42, inverting(0.8, 0.42).calculate(ANY_CANDLES).strength(), 1e-9);
    }

    @Test
    void leavesSilenceSilent() {
        assertEquals(Upside.NEUTRAL, inverting(0, 0).calculate(ANY_CANDLES));
    }

    @Test
    void refusesNothingToInvert() {
        assertThrows(IllegalArgumentException.class, () -> new InvertedUpsideCalculator(null));
    }

    private InvertedUpsideCalculator inverting(double signal, double strength) {
        return new InvertedUpsideCalculator(candles -> new Upside(signal, strength));
    }
}
