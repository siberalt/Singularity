package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothSwitchSignalSourceTest {
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        1L,
        new TimePoint(Instant.parse("2021-06-03T10:00:00Z")),
        Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    private double reading;

    @Test
    void weighsBothSidesEquallyOnTheBoundary() {
        reading = 1;

        assertEquals(0, blending(0.1).calculate(ANY_CANDLES).confidence(), 1e-9);
    }

    /**
     * The point of it: either side of the boundary the same read comes back scaled, not reversed, so
     * a position fades out where the switch would have turned it over.
     */
    @Test
    void scalesTheReadInsteadOfFlippingIt() {
        SmoothSwitchSignalSource calculator = blending(0.1);

        reading = 1.02;
        double justAbove = calculator.calculate(ANY_CANDLES).confidence();

        reading = 1.5;
        double wellAbove = calculator.calculate(ANY_CANDLES).confidence();

        reading = 0.98;
        double justBelow = calculator.calculate(ANY_CANDLES).confidence();

        assertTrue(justAbove > 0 && justAbove < 0.2, "just above the boundary: " + justAbove);
        assertTrue(wellAbove > 0.9, "well above the boundary: " + wellAbove);
        assertTrue(justBelow < 0 && justBelow > -0.2, "just below the boundary: " + justBelow);
    }

    /** Narrowed to nothing it is the switch, which is what makes the two comparable. */
    @Test
    void becomesTheSwitchWithNoWidth() {
        SmoothSwitchSignalSource calculator = blending(0);

        reading = 0.99;
        assertEquals(-1, calculator.calculate(ANY_CANDLES).confidence(), 1e-9);

        reading = 1.01;
        assertEquals(1, calculator.calculate(ANY_CANDLES).confidence(), 1e-9);
    }

    @Test
    void averagesTheStrengthsItWasGiven() {
        reading = 1;

        assertEquals(0.6, blending(0.1).calculate(ANY_CANDLES).strength(), 1e-9);
    }

    @Test
    void countsANegativeWeightAsNoVoteAtAll() {
        SmoothSwitchSignalSource calculator = new SmoothSwitchSignalSource(
            candles -> reading,
            List.of(
                SmoothSwitchSignalSource.weighted(answering(1, 0.4), atAnyReading(-5)),
                SmoothSwitchSignalSource.weighted(answering(-1, 0.8), atAnyReading(1))
            )
        );

        assertEquals(-1, calculator.calculate(ANY_CANDLES).confidence(), 1e-9);
    }

    @Test
    void saysNothingWhenNothingWeighsAnything() {
        SmoothSwitchSignalSource calculator = new SmoothSwitchSignalSource(
            candles -> reading,
            List.of(SmoothSwitchSignalSource.weighted(answering(1, 1), atAnyReading(0)))
        );

        assertEquals(Signal.NEUTRAL, calculator.calculate(ANY_CANDLES));
    }

    @Test
    void refusesToBeBuiltWithNothingToDo() {
        assertThrows(IllegalArgumentException.class,
            () -> new SmoothSwitchSignalSource(null, List.of(
                SmoothSwitchSignalSource.weighted(answering(1, 1), atAnyReading(1)))));
        assertThrows(IllegalArgumentException.class,
            () -> new SmoothSwitchSignalSource(candles -> 0, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> SmoothSwitchSignalSource.weighted(null, atAnyReading(1)));
        assertThrows(IllegalArgumentException.class,
            () -> SmoothSwitchSignalSource.weighted(answering(1, 1), null));
        assertThrows(IllegalArgumentException.class,
            () -> SmoothSwitchSignalSource.rising(1, -0.1));
    }

    /** A trend read and its inversion, weighed either side of a coefficient of one. */
    private SmoothSwitchSignalSource blending(double width) {
        return new SmoothSwitchSignalSource(
            candles -> reading,
            List.of(
                SmoothSwitchSignalSource.weighted(answering(1, 0.4),
                    SmoothSwitchSignalSource.rising(1, width)),
                SmoothSwitchSignalSource.weighted(answering(-1, 0.8),
                    SmoothSwitchSignalSource.falling(1, width))
            )
        );
    }

    private SmoothSwitchSignalSource.Weight atAnyReading(double weight) {
        return reading -> weight;
    }

    private SignalSource answering(double signal, double strength) {
        return candles -> new Signal(signal, strength);
    }
}
