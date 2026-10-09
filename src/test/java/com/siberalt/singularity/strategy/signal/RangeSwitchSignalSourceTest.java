package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RangeSwitchSignalSourceTest {
    private static final List<Candle> ANY_CANDLES = List.of(new Candle(
        1L,
        new TimePoint(Instant.parse("2021-06-03T10:00:00Z")),
        Quotation.of(1), Quotation.of(1), Quotation.of(1), Quotation.of(1), 1
    ));

    private double reading;

    @Test
    void asksWhoeverTheReadingBelongsTo() {
        RangeSwitchSignalSource calculator = switching(
            RangeSwitchSignalSource.Branch.below(1, answering(-0.7)),
            RangeSwitchSignalSource.Branch.from(1, answering(0.7))
        );

        reading = 0.7;
        assertEquals(-0.7, calculator.calculate(ANY_CANDLES).confidence());

        reading = 1.34;
        assertEquals(0.7, calculator.calculate(ANY_CANDLES).confidence());
    }

    /** Half-open, so a reading exactly on a boundary belongs to the range starting there. */
    @Test
    void putsABoundaryReadingInTheRangeItOpens() {
        RangeSwitchSignalSource calculator = switching(
            new RangeSwitchSignalSource.Branch(0, 1, answering(-1)),
            new RangeSwitchSignalSource.Branch(1, 2, answering(1))
        );

        reading = 1;
        assertEquals(1, calculator.calculate(ANY_CANDLES).confidence());
    }

    @Test
    void takesTheFirstRangeThatFitsWhenTheyOverlap() {
        RangeSwitchSignalSource calculator = switching(
            new RangeSwitchSignalSource.Branch(0, 2, answering(-1)),
            new RangeSwitchSignalSource.Branch(1, 3, answering(1))
        );

        reading = 1.5;
        assertEquals(-1, calculator.calculate(ANY_CANDLES).confidence());
    }

    /**
     * A market in a state nothing here was written for gets no opinion, rather than the opinion of
     * whichever branch happened to be nearest.
     */
    @Test
    void saysNothingForAReadingThatBelongsNowhere() {
        RangeSwitchSignalSource calculator = switching(
            new RangeSwitchSignalSource.Branch(0, 1, answering(1))
        );

        reading = 5;
        assertEquals(Signal.NEUTRAL, calculator.calculate(ANY_CANDLES));
    }

    @Test
    void measuresTheReadingFromTheSameCandlesItPassesOn() {
        List<Candle> seen = new java.util.ArrayList<>();

        RangeSwitchSignalSource calculator = new RangeSwitchSignalSource(
            candles -> {
                seen.addAll(candles);

                return 0.5;
            },
            List.of(RangeSwitchSignalSource.Branch.below(1, answering(1)))
        );

        calculator.calculate(ANY_CANDLES);

        assertEquals(ANY_CANDLES, seen);
    }

    @Test
    void refusesToBeBuiltWithNothingToDo() {
        assertThrows(IllegalArgumentException.class,
            () -> new RangeSwitchSignalSource(null, List.of(RangeSwitchSignalSource.Branch.below(1, answering(1)))));
        assertThrows(IllegalArgumentException.class,
            () -> new RangeSwitchSignalSource(candles -> 0, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> RangeSwitchSignalSource.Branch.below(1, null));
        assertThrows(IllegalArgumentException.class,
            () -> new RangeSwitchSignalSource.Branch(2, 1, answering(1)));
    }

    private RangeSwitchSignalSource switching(RangeSwitchSignalSource.Branch... branches) {
        return new RangeSwitchSignalSource(candles -> reading, List.of(branches));
    }

    private SignalSource answering(double signal) {
        return candles -> new Signal(signal, 1);
    }
}
