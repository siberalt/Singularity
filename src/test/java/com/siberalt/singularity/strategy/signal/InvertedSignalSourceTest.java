package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvertedSignalSourceTest {
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        1L,
        new TimePoint(Instant.parse("2021-06-03T10:00:00Z")),
        Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    @Test
    void betsTheOtherWay() {
        assertEquals(-0.8, inverting(0.8, 1).calculate(ANY_CANDLES).confidence(), 1e-9);
        assertEquals(0.8, inverting(-0.8, 1).calculate(ANY_CANDLES).confidence(), 1e-9);
    }

    /** Turning a signal round says nothing about how sure it was. */
    @Test
    void keepsTheStrengthUntouched() {
        assertEquals(0.42, inverting(0.8, 0.42).calculate(ANY_CANDLES).strength(), 1e-9);
    }

    @Test
    void leavesSilenceSilent() {
        assertEquals(Signal.NEUTRAL, invertingSilence().calculate(ANY_CANDLES));
    }

    @Test
    void refusesNothingToInvert() {
        assertThrows(IllegalArgumentException.class, () -> new InvertedSignalSource(null));
    }

    /** Настоящее молчание, а не посчитанный ноль: тип их теперь различает. */
    private InvertedSignalSource invertingSilence() {
        return new InvertedSignalSource(candles -> Signal.NEUTRAL);
    }

    private InvertedSignalSource inverting(double signal, double strength) {
        return new InvertedSignalSource(candles -> new Signal(signal, strength));
    }
}
