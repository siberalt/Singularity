package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCondition;

import java.util.List;
import java.util.function.Supplier;

/**
 * Passes the delegate's answer on while a condition holds, and says nothing while it does not.
 * <p>
 * The condition is asked first, and a refusal is the whole answer: {@link Upside#NEUTRAL}. When it passes,
 * the delegate's answer goes out untouched - including its own {@code NEUTRAL}, because a filter is not a
 * signal and has nothing to add to one.
 * <p>
 * Whether the delegate is consulted at all is up to the condition. A {@link MarketCondition} reads the
 * market and nothing else, so on a refused bar the delegate is never called. A {@link SignalCondition} is
 * handed the signal as a {@link Supplier} and may look at it - a dead band around zero and a condition
 * whose meaning depends on the side both have to. The supplier calls the delegate at most once per bar,
 * however many times it is asked, and that same answer is what goes out.
 * <p>
 * What belongs in the condition is something the market is doing - a session too quiet to pay for a round
 * trip, an instrument that has stopped printing, an hour of the day, a regime measured by
 * {@link com.siberalt.singularity.strategy.analysis.VarianceRatio} - or something about the signal that the
 * strategy's own thresholds cannot express. A dead band is the plain case: a side-only signal is ±1, so
 * {@code buyThreshold} has nothing to measure, and "ignore the crossing while the line is hovering at zero"
 * can only be said here. What does not belong is a threshold the strategy already applies to the same
 * signal: two places saying the same thing will disagree sooner or later.
 * <p>
 * Three things follow from how a refused bar works, and the first two have cost real money in this project:
 * <ul>
 *   <li><b>A delegate that counts bars stops counting.</b> {@link FixedSignalReverserUpsideCalculator} and
 *   {@link EntryExitUpsideCalculator} both measure a holding time in calls, so a filter that refuses ten
 *   bars in the middle of a trade stretches that trade by ten bars of wall-clock time without either of
 *   them knowing.</li>
 *   <li><b>A signal given on a refused bar is lost, not deferred.</b> The condition says "not this bar",
 *   never "later" - so wrapping a pair that opens and closes a position can swallow the close and leave
 *   the position with nothing to end it. Filter the thing that opens a trade, and leave what ends it
 *   alone.</li>
 *   <li><b>A condition that reads the signal wakes the delegate even when it refuses.</b> The counter of a
 *   bar-counting delegate moves on such a bar although nothing came out, which is the same hazard as the
 *   first one but arriving from the other side.</li>
 * </ul>
 */
public class FilterUpsideCalculator implements UpsideCalculator {
    private final UpsideCalculator delegate;

    private final SignalCondition filter;

    /**
     * @param delegate where the signal comes from while the condition holds
     * @param filter   what has to hold for the signal to be passed on at all
     */
    public FilterUpsideCalculator(UpsideCalculator delegate, SignalCondition filter) {
        if (delegate == null) {
            throw new IllegalArgumentException("There is nothing to filter without a delegate");
        }

        if (filter == null) {
            throw new IllegalArgumentException("A filter that lets everything through is not a filter");
        }

        this.delegate = delegate;
        this.filter = filter;
    }

    /** A condition that reads the market alone, and so never wakes the delegate on a refused bar. */
    public FilterUpsideCalculator(UpsideCalculator delegate, MarketCondition filter) {
        this(delegate, SignalCondition.of(filter));
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        Supplier<Upside> signal = askedOnce(lastCandles);

        return filter.holds(lastCandles, signal) ? signal.get() : Upside.NEUTRAL;
    }

    /**
     * The delegate behind a supplier that calls it once and remembers the answer.
     * <p>
     * Once, because the condition and the caller both want the same reading of the same bar: asking twice
     * would pay for the calculation twice and, for a delegate that carries state between calls, would not
     * even return the same thing.
     */
    private Supplier<Upside> askedOnce(List<Candle> lastCandles) {
        return new Supplier<>() {
            private Upside answer;
            private boolean asked;

            @Override
            public Upside get() {
                if (!asked) {
                    answer = delegate.calculate(lastCandles);
                    asked = true;
                }

                return answer;
            }
        };
    }
}
