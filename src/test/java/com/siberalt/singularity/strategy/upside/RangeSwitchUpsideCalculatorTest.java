package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RangeSwitchUpsideCalculatorTest {
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        "TEST",
        new TimePoint(Instant.parse("2021-06-03T10:00:00Z")),
        Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    private double reading;

    @Test
    void asksWhoeverTheReadingBelongsTo() {
        RangeSwitchUpsideCalculator calculator = switching(
            RangeSwitchUpsideCalculator.Branch.below(1, answering(-0.7)),
            RangeSwitchUpsideCalculator.Branch.from(1, answering(0.7))
        );

        reading = 0.7;
        assertEquals(-0.7, calculator.calculate(ANY_CANDLES).signal());

        reading = 1.34;
        assertEquals(0.7, calculator.calculate(ANY_CANDLES).signal());
    }

    /** Half-open, so a reading exactly on a boundary belongs to the range starting there. */
    @Test
    void putsABoundaryReadingInTheRangeItOpens() {
        RangeSwitchUpsideCalculator calculator = switching(
            new RangeSwitchUpsideCalculator.Branch(0, 1, answering(-1)),
            new RangeSwitchUpsideCalculator.Branch(1, 2, answering(1))
        );

        reading = 1;
        assertEquals(1, calculator.calculate(ANY_CANDLES).signal());
    }

    @Test
    void takesTheFirstRangeThatFitsWhenTheyOverlap() {
        RangeSwitchUpsideCalculator calculator = switching(
            new RangeSwitchUpsideCalculator.Branch(0, 2, answering(-1)),
            new RangeSwitchUpsideCalculator.Branch(1, 3, answering(1))
        );

        reading = 1.5;
        assertEquals(-1, calculator.calculate(ANY_CANDLES).signal());
    }

    /**
     * A market in a state nothing here was written for gets no opinion, rather than the opinion of
     * whichever branch happened to be nearest.
     */
    @Test
    void saysNothingForAReadingThatBelongsNowhere() {
        RangeSwitchUpsideCalculator calculator = switching(
            new RangeSwitchUpsideCalculator.Branch(0, 1, answering(1))
        );

        reading = 5;
        assertEquals(Upside.NEUTRAL, calculator.calculate(ANY_CANDLES));
    }

    @Test
    void measuresTheReadingFromTheSameCandlesItPassesOn() {
        List<Candle> seen = new java.util.ArrayList<>();

        RangeSwitchUpsideCalculator calculator = new RangeSwitchUpsideCalculator(
            candles -> {
                seen.addAll(candles);

                return 0.5;
            },
            List.of(RangeSwitchUpsideCalculator.Branch.below(1, answering(1)))
        );

        calculator.calculate(ANY_CANDLES);

        assertEquals(ANY_CANDLES, seen);
    }

    @Test
    void refusesToBeBuiltWithNothingToDo() {
        assertThrows(IllegalArgumentException.class,
            () -> new RangeSwitchUpsideCalculator(null, List.of(RangeSwitchUpsideCalculator.Branch.below(1, answering(1)))));
        assertThrows(IllegalArgumentException.class,
            () -> new RangeSwitchUpsideCalculator(candles -> 0, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> RangeSwitchUpsideCalculator.Branch.below(1, null));
        assertThrows(IllegalArgumentException.class,
            () -> new RangeSwitchUpsideCalculator.Branch(2, 1, answering(1)));
    }

    private RangeSwitchUpsideCalculator switching(RangeSwitchUpsideCalculator.Branch... branches) {
        return new RangeSwitchUpsideCalculator(candles -> reading, List.of(branches));
    }

    private UpsideCalculator answering(double signal) {
        return candles -> new Upside(signal, 1);
    }
}
