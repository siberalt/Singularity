package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.market.MarketCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Условие может заглянуть в сигнал, но делегата это разбудит один раз, сколько бы раз условие ни
     * спросило. Иначе расчёт платится дважды, а делегат с состоянием на второй вызов ответит другое.
     */
    @Test
    void should_AskTheDelegateOnce_WhenTheConditionReadsTheSignalToo() {
        List<Integer> asked = new ArrayList<>();
        UpsideCalculator delegate = candles -> {
            asked.add(1);

            return of(1);
        };
        var calculator = new FilterUpsideCalculator(delegate, (candles, signal) -> {
            signal.get();
            signal.get();

            return signal.get().signal() > 0;
        });

        assertEquals(of(1), calculator.calculate(ANY_CANDLES));
        assertEquals(1, asked.size());
    }

    /** И наружу уходит ровно то, что видело условие, а не свежий вызов делегата. */
    @Test
    void should_ReturnTheReading_TheConditionSaw() {
        double[] next = {1};
        UpsideCalculator counting = candles -> of(next[0]++);
        List<Upside> seen = new ArrayList<>();
        var calculator = new FilterUpsideCalculator(counting, (candles, signal) -> {
            seen.add(signal.get());

            return true;
        });

        Upside answered = calculator.calculate(ANY_CANDLES);

        assertEquals(of(1), seen.getFirst());
        assertEquals(seen.getFirst(), answered);
    }

    /** Условию, которому сигнал не нужен, он и не достаётся: делегат молчит, даже когда фильтр пропускает. */
    @Test
    void should_LeaveTheDelegateAlone_WhenASignalConditionNeverLooks() {
        List<Integer> asked = new ArrayList<>();
        UpsideCalculator delegate = candles -> {
            asked.add(1);

            return of(1);
        };

        assertEquals(Upside.NEUTRAL,
            new FilterUpsideCalculator(delegate, (candles, signal) -> false).calculate(ANY_CANDLES));
        assertEquals(List.of(), asked);
    }

    /** Условие о рынке - частный случай условия о сигнале, и сигнала оно не касается. */
    @Test
    void should_TakeAMarketCondition_AsASignalConditionThatIgnoresTheSignal() {
        assertEquals(of(1), SignalCondition.of(candles -> true).holds(ANY_CANDLES, () -> of(1))
            ? of(1) : Upside.NEUTRAL);
        assertFalse(SignalCondition.of(candles -> false).holds(ANY_CANDLES, () -> {
            throw new AssertionError("Условию о рынке сигнал не нужен");
        }));
    }

    /** Второе условие не спрашивают, если первое отказало, - и сигнал тогда тоже остаётся непрошенным. */
    @Test
    void should_ShortCircuit_WhenTheFirstConditionRefuses() {
        SignalCondition both = SignalCondition.of(candles -> false)
            .and((candles, signal) -> signal.get().signal() > 0);

        assertFalse(both.holds(ANY_CANDLES, () -> {
            throw new AssertionError("Первое условие уже отказало");
        }));
    }

    @Test
    void should_Throw_WhenEitherHalfIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> new FilterUpsideCalculator(null, candles -> true));
        assertThrows(IllegalArgumentException.class,
            () -> new FilterUpsideCalculator(saying(of(1)), (MarketCondition) null));
        assertThrows(IllegalArgumentException.class,
            () -> new FilterUpsideCalculator(saying(of(1)), (SignalCondition) null));
        assertThrows(IllegalArgumentException.class, () -> SignalCondition.of(null));
        assertThrows(IllegalArgumentException.class,
            () -> SignalCondition.of(candles -> true).and(null));
    }
}
