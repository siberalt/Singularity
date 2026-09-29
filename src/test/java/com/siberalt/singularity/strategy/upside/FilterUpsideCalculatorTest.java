package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterUpsideCalculatorTest {
    private static final List<Candle> ANY_CANDLES = List.of(
        Candle.of(TimePoint.NULL, 100, 100, 100, 100, 100)
    );

    private Upside of(double signal) {
        return new Upside(signal, 1);
    }

    private UpsideCalculator saying(Upside upside) {
        return candles -> upside;
    }

    @Test
    void should_PassTheSignalThrough_WhenTheFilterHolds() {
        var calculator = new FilterUpsideCalculator(saying(new Upside(0.8, 0.5)), candles -> true);

        assertEquals(new Upside(0.8, 0.5), calculator.calculate(ANY_CANDLES));
    }

    @Test
    void should_ReturnNeutral_WhenTheFilterRefuses() {
        var calculator = new FilterUpsideCalculator(saying(of(1)), candles -> false);

        assertEquals(Upside.NEUTRAL, calculator.calculate(ANY_CANDLES));
    }

    /** Both ways round: a filter is not a side. */
    @Test
    void should_PassAFallThrough_AsReadily() {
        var calculator = new FilterUpsideCalculator(saying(of(-1)), candles -> true);

        assertEquals(of(-1), calculator.calculate(ANY_CANDLES));
    }

    /** A filter has nothing to add to silence, so the delegate's own NEUTRAL comes out unchanged. */
    @Test
    void should_KeepTheDelegatesSilence_WhenTheFilterHolds() {
        var calculator = new FilterUpsideCalculator(saying(Upside.NEUTRAL), candles -> true);

        assertEquals(Upside.NEUTRAL, calculator.calculate(ANY_CANDLES));
    }

    @Test
    void should_NotAskTheDelegate_WhenTheFilterRefuses() {
        List<Integer> asked = new ArrayList<>();
        var calculator = new FilterUpsideCalculator(candles -> {
            asked.add(1);

            return of(1);
        }, candles -> false);

        calculator.calculate(ANY_CANDLES);

        assertEquals(List.of(), asked);
    }

    /** The filter reads the same candles the signal does. */
    @Test
    void should_HandTheCandlesToTheFilter() {
        List<List<Candle>> seen = new ArrayList<>();
        var calculator = new FilterUpsideCalculator(saying(of(1)), candles -> {
            seen.add(candles);

            return true;
        });

        calculator.calculate(ANY_CANDLES);

        assertEquals(List.of(ANY_CANDLES), seen);
    }

    /** Asked on every bar, so a condition that comes and goes lets the signal through and stops it again. */
    @Test
    void should_AskTheFilter_OnEveryBar() {
        boolean[] holds = {true};
        var calculator = new FilterUpsideCalculator(saying(of(1)), candles -> holds[0]);

        assertEquals(of(1), calculator.calculate(ANY_CANDLES));

        holds[0] = false;

        assertEquals(Upside.NEUTRAL, calculator.calculate(ANY_CANDLES));

        holds[0] = true;

        assertEquals(of(1), calculator.calculate(ANY_CANDLES));
    }

    @Test
    void should_Throw_WhenEitherHalfIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> new FilterUpsideCalculator(null, candles -> true));
        assertThrows(IllegalArgumentException.class,
            () -> new FilterUpsideCalculator(saying(of(1)), null));
    }
}
