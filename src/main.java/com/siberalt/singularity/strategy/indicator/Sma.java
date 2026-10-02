package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.function.Function;

/**
 * Simple moving average: the plain mean of the last {@code period} prices.
 * <p>
 * Fed price by price, it keeps the last {@code period} of them and their sum, so each price costs O(1)
 * where recomputing a window costs O(period). An instance follows one series and is not reused for
 * another; the static {@link #of(List, int)} methods are the one-shot form over a list.
 * <p>
 * Until {@code period} prices have arrived there is no average of that width, and {@link #value()} says
 * so with {@link Double#NaN} rather than with the mean of fewer - a caller that wants a shorter average
 * while it waits can ask for one.
 */
public class Sma {
    private final int period;

    private final Function<Candle, Double> priceExtractor;

    private final double[] window;

    private double sum;

    private int count;

    public Sma(int period, Function<Candle, Double> priceExtractor) {
        if (period < 1) {
            throw new IllegalArgumentException("The SMA period must be positive, got " + period);
        }

        this.period = period;
        this.priceExtractor = priceExtractor;
        this.window = new double[period];
    }

    /** An SMA by closes. */
    public Sma(int period) {
        this(period, Candle::getCloseAsDouble);
    }

    /** Adds a candle's price and returns the reading after it. */
    public double add(Candle candle) {
        return add(priceExtractor.apply(candle));
    }

    /** Adds a price and returns the reading after it. */
    public double add(double price) {
        int slot = count % period;

        if (count >= period) {
            sum -= window[slot];
        }

        window[slot] = price;
        sum += price;
        count++;

        return value();
    }

    /** The reading after the last price added, or {@link Double#NaN} while fewer than {@code period} have arrived. */
    public double value() {
        return ready() ? sum / period : Double.NaN;
    }

    public boolean ready() {
        return count >= period;
    }

    /** Forgets everything added. */
    public void reset() {
        sum = 0;
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

        Sma sma = new Sma(period, priceExtractor);

        for (Candle candle : candles) {
            sma.add(candle);
        }

        return sma.value();
    }

    /** The reading at every one of these candles, {@link Double#NaN} until there have been enough. */
    public static double[] seriesOf(List<Candle> candles, int period, Function<Candle, Double> priceExtractor) {
        double[] readings = new double[candles.size()];
        Sma sma = new Sma(period, priceExtractor);

        for (int at = 0; at < candles.size(); at++) {
            readings[at] = sma.add(candles.get(at));
        }

        return readings;
    }
}
