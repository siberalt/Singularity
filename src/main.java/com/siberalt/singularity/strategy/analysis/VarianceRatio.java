package com.siberalt.singularity.strategy.analysis;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Whether an instrument's moves carry on or come back, which decides what kind of signal can work
 * on it at all - before any signal is written.
 * <p>
 * The ratio is the variance of a move over {@code horizon} bars against {@code horizon} times the
 * variance of one bar. A price that wanders at random spreads out in proportion to time and the
 * ratio sits at one. Above one the moves add to each other, which is what a trend signal needs;
 * below one they cancel, and a trend signal there is not merely useless but backwards - the same
 * signal inverted is the one that works.
 * <p>
 * Worth knowing because volatility alone does not answer this and is routinely mistaken for it. Two
 * instruments measured here moved almost identically per hour - eighty-odd basis points of range -
 * and a trend signal earned on one and lost on the other; what told them apart was this ratio,
 * 1.34 against 0.70.
 */
public class VarianceRatio {
    /**
     * @param candles ordered oldest first, one instrument, one interval
     * @param horizon bars in the longer move, at least two
     */
    public double measure(List<Candle> candles, int horizon) {
        if (horizon < 2) {
            throw new IllegalArgumentException("Horizon must span at least two bars, got " + horizon);
        }

        double singleBar = variance(logReturns(candles, 1));

        if (singleBar == 0) {
            return 0;
        }

        return variance(logReturns(candles, horizon)) / (horizon * singleBar);
    }

    /**
     * Overlapping returns, so a short history still yields a usable count. They are not independent
     * of one another, which matters for judging how far from one a reading has to be before it
     * means anything, but not for the estimate itself.
     */
    protected double[] logReturns(List<Candle> candles, int step) {
        double[] returns = new double[Math.max(0, candles.size() - step)];

        for (int bar = 0; bar + step < candles.size(); bar++) {
            double from = candles.get(bar).getCloseAsDouble();
            double to = candles.get(bar + step).getCloseAsDouble();
            returns[bar] = from > 0 && to > 0 ? Math.log(to / from) : 0;
        }

        return returns;
    }

    protected double variance(double[] values) {
        if (values.length < 2) {
            return 0;
        }

        double mean = 0;

        for (double value : values) {
            mean += value;
        }

        mean /= values.length;
        double sum = 0;

        for (double value : values) {
            sum += (value - mean) * (value - mean);
        }

        return sum / (values.length - 1);
    }
}
