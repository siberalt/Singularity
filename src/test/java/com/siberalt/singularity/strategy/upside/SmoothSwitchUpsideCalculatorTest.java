package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmoothSwitchUpsideCalculatorTest {
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        "TEST",
        new TimePoint(Instant.parse("2021-06-03T10:00:00Z")),
        Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    private double reading;

    @Test
    void weighsBothSidesEquallyOnTheBoundary() {
        reading = 1;

        assertEquals(0, blending(0.1).calculate(ANY_CANDLES).signal(), 1e-9);
    }

    /**
     * The point of it: either side of the boundary the same read comes back scaled, not reversed, so
     * a position fades out where the switch would have turned it over.
     */
    @Test
    void scalesTheReadInsteadOfFlippingIt() {
        SmoothSwitchUpsideCalculator calculator = blending(0.1);

        reading = 1.02;
        double justAbove = calculator.calculate(ANY_CANDLES).signal();

        reading = 1.5;
        double wellAbove = calculator.calculate(ANY_CANDLES).signal();

        reading = 0.98;
        double justBelow = calculator.calculate(ANY_CANDLES).signal();

        assertTrue(justAbove > 0 && justAbove < 0.2, "just above the boundary: " + justAbove);
        assertTrue(wellAbove > 0.9, "well above the boundary: " + wellAbove);
        assertTrue(justBelow < 0 && justBelow > -0.2, "just below the boundary: " + justBelow);
    }

    /** Narrowed to nothing it is the switch, which is what makes the two comparable. */
    @Test
    void becomesTheSwitchWithNoWidth() {
        SmoothSwitchUpsideCalculator calculator = blending(0);

        reading = 0.99;
        assertEquals(-1, calculator.calculate(ANY_CANDLES).signal(), 1e-9);

        reading = 1.01;
        assertEquals(1, calculator.calculate(ANY_CANDLES).signal(), 1e-9);
    }

    @Test
    void averagesTheStrengthsItWasGiven() {
        reading = 1;

        assertEquals(0.6, blending(0.1).calculate(ANY_CANDLES).strength(), 1e-9);
    }

    @Test
    void countsANegativeWeightAsNoVoteAtAll() {
        SmoothSwitchUpsideCalculator calculator = new SmoothSwitchUpsideCalculator(
            candles -> reading,
            List.of(
                SmoothSwitchUpsideCalculator.weighted(answering(1, 0.4), atAnyReading(-5)),
                SmoothSwitchUpsideCalculator.weighted(answering(-1, 0.8), atAnyReading(1))
            )
        );

        assertEquals(-1, calculator.calculate(ANY_CANDLES).signal(), 1e-9);
    }

    @Test
    void saysNothingWhenNothingWeighsAnything() {
        SmoothSwitchUpsideCalculator calculator = new SmoothSwitchUpsideCalculator(
            candles -> reading,
            List.of(SmoothSwitchUpsideCalculator.weighted(answering(1, 1), atAnyReading(0)))
        );

        assertEquals(Upside.NEUTRAL, calculator.calculate(ANY_CANDLES));
    }

    @Test
    void refusesToBeBuiltWithNothingToDo() {
        assertThrows(IllegalArgumentException.class,
            () -> new SmoothSwitchUpsideCalculator(null, List.of(
                SmoothSwitchUpsideCalculator.weighted(answering(1, 1), atAnyReading(1)))));
        assertThrows(IllegalArgumentException.class,
            () -> new SmoothSwitchUpsideCalculator(candles -> 0, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> SmoothSwitchUpsideCalculator.weighted(null, atAnyReading(1)));
        assertThrows(IllegalArgumentException.class,
            () -> SmoothSwitchUpsideCalculator.weighted(answering(1, 1), null));
        assertThrows(IllegalArgumentException.class,
            () -> SmoothSwitchUpsideCalculator.rising(1, -0.1));
    }

    /** A trend read and its inversion, weighed either side of a coefficient of one. */
    private SmoothSwitchUpsideCalculator blending(double width) {
        return new SmoothSwitchUpsideCalculator(
            candles -> reading,
            List.of(
                SmoothSwitchUpsideCalculator.weighted(answering(1, 0.4),
                    SmoothSwitchUpsideCalculator.rising(1, width)),
                SmoothSwitchUpsideCalculator.weighted(answering(-1, 0.8),
                    SmoothSwitchUpsideCalculator.falling(1, width))
            )
        );
    }

    private SmoothSwitchUpsideCalculator.Weight atAnyReading(double weight) {
        return reading -> weight;
    }

    private UpsideCalculator answering(double signal, double strength) {
        return candles -> new Upside(signal, strength);
    }
}
