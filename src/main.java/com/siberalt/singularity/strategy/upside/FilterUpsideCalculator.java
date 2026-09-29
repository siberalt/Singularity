package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCondition;

import java.util.List;

/**
 * Passes the delegate's answer on while a condition holds, and says nothing while it does not.
 * <p>
 * The condition is asked first, and a refusal is the whole answer: {@link Upside#NEUTRAL}, with the
 * delegate not consulted at all. When it passes, the delegate's answer goes out untouched - including its
 * own {@code NEUTRAL}, because a filter is not a signal and has nothing to add to one.
 * <p>
 * What belongs in the condition is something the market is doing - a session too quiet to pay for a round
 * trip, an instrument that has stopped printing, an hour of the day, a regime measured by
 * {@link com.siberalt.singularity.strategy.analysis.VarianceRatio}. What does not belong is the signal's
 * own strength read a second time: that is a threshold, and the calculators that take one already have it.
 * <p>
 * Two things follow from the delegate not being asked on a refused bar, and both have cost real money in
 * this project:
 * <ul>
 *   <li><b>A delegate that counts bars stops counting.</b> {@link FixedSignalReverserUpsideCalculator} and
 *   {@link EntryExitUpsideCalculator} both measure a holding time in calls, so a filter that refuses ten
 *   bars in the middle of a trade stretches that trade by ten bars of wall-clock time without either of
 *   them knowing.</li>
 *   <li><b>A signal given on a refused bar is lost, not deferred.</b> The condition says "not this bar",
 *   never "later" - so wrapping a pair that opens and closes a position can swallow the close and leave
 *   the position with nothing to end it. Filter the thing that opens a trade, and leave what ends it
 *   alone.</li>
 * </ul>
 */
public class FilterUpsideCalculator implements UpsideCalculator {
    private final UpsideCalculator delegate;

    private final MarketCondition filter;

    /**
     * @param delegate where the signal comes from while the condition holds
     * @param filter   what has to hold for the signal to be passed on at all
     */
    public FilterUpsideCalculator(UpsideCalculator delegate, MarketCondition filter) {
        if (delegate == null) {
            throw new IllegalArgumentException("There is nothing to filter without a delegate");
        }

        if (filter == null) {
            throw new IllegalArgumentException("A filter that lets everything through is not a filter");
        }

        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        return filter.holds(lastCandles) ? delegate.calculate(lastCandles) : Upside.NEUTRAL;
    }
}
