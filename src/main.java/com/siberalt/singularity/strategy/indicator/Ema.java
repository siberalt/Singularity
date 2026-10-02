package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.function.Function;

/**
 * Exponential moving average: every price is smoothed into the average with a weight of
 * {@code 2 / (period + 1)}, so recent prices count for more and old ones fade without ever dropping out.
 * <p>
 * The first {@code period} prices are averaged plainly and that mean is the starting point - the usual
 * way to seed it, and the one that makes the first reading an {@link Sma}'s. After that each price costs
 * O(1) and the state is a single number. An instance follows one series and is not reused for another;
 * the static {@link #of(List, int)} methods are the one-shot form over a list.
 * <p>
 * Until {@code period} prices have arrived there is no reading, and {@link #value()} says so with
 * {@link Double#NaN}, as {@link IncrementalRsi} does.
 */
public class Ema {
    private final int period;

    private final double alpha;

    private final Function<Candle, Double> priceExtractor;

    private double average;

    private int count;

    public Ema(int period, Function<Candle, Double> priceExtractor) {
        if (period < 1) {
            throw new IllegalArgumentException("The EMA period must be positive, got " + period);
        }

        this.period = period;
        this.alpha = 2.0 / (period + 1);
        this.priceExtractor = priceExtractor;
    }

    /** An EMA by closes. */
    public Ema(int period) {
        this(period, Candle::getCloseAsDouble);
    }

    /** Adds a candle's price and returns the reading after it. */
    public double add(Candle candle) {
        return add(priceExtractor.apply(candle));
    }

    /** Adds a price and returns the reading after it. */
    public double add(double price) {
        count++;

        if (count <= period) {
            average += price / period;
        } else {
            average += alpha * (price - average);
        }

        return value();
    }

    /** The reading after the last price added, or {@link Double#NaN} while fewer than {@code period} have arrived. */
    public double value() {
        return ready() ? average : Double.NaN;
    }

    public boolean ready() {
        return count >= period;
    }

    /** Forgets everything added. */
    public void reset() {
        average = 0;
        count = 0;
    }

    /** The reading at the last of these candles by their closes, or {@link Double#NaN} when there are too few. */
    public static double of(List<Candle> candles, int period) {
        return of(candles, period, Candle::getCloseAsDouble);
    }

    public static double of(List<Candle> candles, int period, Function<Candle, Double> priceExtractor) {
        if (candles == null) {
            return Double.NaN;
        }

        Ema ema = new Ema(period, priceExtractor);

        for (Candle candle : candles) {
            ema.add(candle);
        }

        return ema.value();
    }

    /** The reading at every one of these candles, {@link Double#NaN} until there have been enough. */
    public static double[] seriesOf(List<Candle> candles, int period, Function<Candle, Double> priceExtractor) {
        double[] readings = new double[candles.size()];
        Ema ema = new Ema(period, priceExtractor);

        for (int at = 0; at < candles.size(); at++) {
            readings[at] = ema.add(candles.get(at));
        }

        return readings;
    }
}
