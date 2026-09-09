package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizedUpsideCalculatorTest {
    private static final Instant AT = Instant.parse("2021-06-03T10:00:00Z");
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        "TEST", new TimePoint(AT), Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    private double next;
    private double strength = 1;

    private final NormalizedUpsideCalculator calculator =
        new NormalizedUpsideCalculator(candles -> new Upside(next, strength)).setWarmup(3);

    /**
     * The reason this exists: a signal that never leaves a tenth would never cross a threshold set
     * near one, and the strategy would read that as a signal finding nothing.
     */
    @Test
    void turnsATypicalReadingIntoOne() {
        feed(0.1, 0.1, 0.1);

        assertEquals(Math.tanh(1), signalFor(0.1), 1e-9);
    }

    @Test
    void scalesWithHowUnusualTheReadingIs() {
        feed(0.1, 0.1, 0.1);

        assertEquals(Math.tanh(2), signalFor(0.2), 1e-9);
        // That reading joined the rest, so the typical strength is now an eighth, not a tenth.
        assertEquals(-Math.tanh(0.3 / 0.125), signalFor(-0.3), 1e-9);
    }

    @Test
    void saysNothingUntilItHasSeenEnoughToScaleAgainst() {
        assertEquals(Upside.NEUTRAL, upsideFor(0.1));
        assertEquals(Upside.NEUTRAL, upsideFor(0.1));
        assertEquals(Upside.NEUTRAL, upsideFor(0.1));

        assertEquals(Math.tanh(1), signalFor(0.1), 1e-9);
    }

    /**
     * A calculator that declines to answer is not answering weakly. Scaling its silence up would
     * invent an opinion, and counting it towards the scale would make every real reading look
     * stronger than it is.
     */
    @Test
    void letsSilencePassThroughAndKeepsItOutOfTheScale() {
        feed(0.1, 0.1, 0.1);

        assertEquals(0, signalFor(0));
        // Still one, not two thirds of one - the zero was not counted.
        assertEquals(Math.tanh(1), signalFor(0.1), 1e-9);
    }

    @Test
    void forgetsReadingsPastItsMemory() {
        calculator.setMemory(2);
        feed(1.0, 1.0, 1.0);

        // The two remembered readings are both a tenth, so a tenth is now typical.
        feed(0.1, 0.1);

        assertEquals(Math.tanh(1), signalFor(0.1), 1e-9);
    }

    @Test
    void keepsTheStrengthItWasGiven() {
        feed(0.1, 0.1, 0.1);
        strength = 0.42;

        assertEquals(0.42, upsideFor(0.1).strength());
    }

    @Test
    void refusesNothingToNormalize() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizedUpsideCalculator(null));
    }

    private void feed(double... signals) {
        for (double signal : signals) {
            upsideFor(signal);
        }
    }

    private double signalFor(double signal) {
        return upsideFor(signal).signal();
    }

    private Upside upsideFor(double signal) {
        next = signal;

        return calculator.calculate(ANY_CANDLES);
    }
}
