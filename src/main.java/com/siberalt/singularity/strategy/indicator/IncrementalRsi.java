package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Wilder's RSI, taking closes one at a time and remembering where it got to.
 * <p>
 * The one place this is written down. It had been written three times - in
 * {@link com.siberalt.singularity.strategy.signal.RSISignalSource}, in
 * {@link com.siberalt.singularity.strategy.impl.RsiLimitEntryStrategy} and in a simulation - which is
 * three chances for the readings that a measurement is based on and the readings a strategy trades on to
 * drift apart without anyone noticing. They agreed, as it happens; keeping them agreeing was the reason
 * to have one copy.
 * <p>
 * The calculation: the first {@code period} changes are averaged plainly, and every change after that is
 * smoothed into the average with a weight of one over the period. Then
 * {@code 100 - 100 / (1 + averageGain / averageLoss)}. Feeding it bar by bar costs O(1) a bar where
 * recomputing a window costs O(period), which matters when every bar of several hundred thousand needs a
 * reading.
 * <p>
 * Before {@code period} changes have arrived there is no reading, and {@link #value()} says so with
 * {@link Double#NaN} rather than with a number. Fifty would be a number - the middle of the scale, the
 * value of a market in perfect balance - and a caller that wants to draw or trade the middle while it
 * waits can say so itself; one that forgets to check would silently treat "nothing yet" as "balanced".
 * <p>
 * The state is the previous close, how many changes have arrived and the two averages, so an instance
 * follows one series of bars and is not reused for another.
 */
public class IncrementalRsi {
    /** What an instrument in perfect balance reads, and what a caller may want while there is no reading. */
    public static final double BALANCED = 50;

    private final int period;

    private double previousClose = Double.NaN;

    private double averageGain;

    private double averageLoss;

    private int changes;

    public IncrementalRsi(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("The RSI period must be positive, got " + period);
        }

        this.period = period;
    }

    public IncrementalRsi() {
        this(14);
    }

    /** Adds a bar's close and returns the reading after it. */
    public double add(Candle candle) {
        return add(candle.getCloseAsDouble());
    }

    /**
     * Adds a close and returns the reading after it. The first close only becomes the one the next change
     * is measured against - a change needs two prices, and before the first there is only one.
     */
    public double add(double close) {
        if (!Double.isNaN(previousClose)) {
            double change = close - previousClose;
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);

            changes++;

            if (changes <= period) {
                averageGain += gain / period;
                averageLoss += loss / period;
            } else {
                averageGain = (averageGain * (period - 1) + gain) / period;
                averageLoss = (averageLoss * (period - 1) + loss) / period;
            }
        }

        previousClose = close;

        return value();
    }

    /**
     * The reading after the last close added, or {@link Double#NaN} while fewer than {@code period}
     * changes have arrived.
     * <p>
     * A stretch without a single fall reads as a hundred, and one without a single rise as nothing at all;
     * a stretch that did not move either way is {@link #BALANCED}, because a ratio of nothing to nothing
     * is not a reason to call an instrument dear or cheap.
     */
    public double value() {
        if (!ready()) {
            return Double.NaN;
        }

        if (averageLoss == 0) {
            return averageGain == 0 ? BALANCED : 100;
        }

        return 100 - 100 / (1 + averageGain / averageLoss);
    }

    public boolean ready() {
        return changes >= period;
    }

    /**
     * The reading at the last of these candles, or {@link Double#NaN} when there are not enough of them.
     * All of them are used, not just the last {@code period + 1}: Wilder's smoothing carries every change
     * forward, so a longer history is a different - and steadier - number.
     */
    public static double of(List<Candle> candles, int period) {
        if (candles == null) {
            return Double.NaN;
        }

        IncrementalRsi rsi = new IncrementalRsi(period);

        for (Candle candle : candles) {
            rsi.add(candle);
        }

        return rsi.value();
    }

    /** The reading at every one of these candles, {@link Double#NaN} until there have been enough. */
    public static double[] seriesOf(List<Candle> candles, int period) {
        double[] readings = new double[candles.size()];
        IncrementalRsi rsi = new IncrementalRsi(period);

        for (int at = 0; at < candles.size(); at++) {
            readings[at] = rsi.add(candles.get(at));
        }

        return readings;
    }
}
