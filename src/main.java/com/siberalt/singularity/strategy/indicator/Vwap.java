package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

/**
 * Volume weighted average price: the sum of price times volume over the sum of volume.
 * <p>
 * The one place this is written down, so that the reading a signal is based on and the reading anything
 * else measures against it are the same number.
 * <p>
 * It can be fed candle by candle: the state is just the two sums, so each candle costs O(1) where
 * recomputing a list costs O(n). An instance follows one series and is not reused for another; the static
 * {@link #of(List)} methods are the one-shot form over a list.
 * <p>
 * With no candles, or with no volume in them, there is no average to speak of, and {@link #value()} says
 * so with {@link Double#NaN} rather than with a made-up price.
 */
public class Vwap {
    private final Function<Candle, Double> priceExtractor;

    private double totalValue;

    private double totalVolume;

    /** A VWAP taking the price of each candle with {@code priceExtractor}. */
    public Vwap(Function<Candle, Double> priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    /** A VWAP by closes. */
    public Vwap() {
        this(Candle::getCloseAsDouble);
    }

    /** Adds a candle and returns the reading after it. */
    public double add(Candle candle) {
        return add(priceExtractor.apply(candle), candle.volume());
    }

    /** Adds a trade-like pair of a price and a volume and returns the reading after it. */
    public double add(double price, double volume) {
        totalValue += price * volume;
        totalVolume += volume;

        return value();
    }

    /** The reading after everything added, or {@link Double#NaN} while there is no volume. */
    public double value() {
        return ready() ? totalValue / totalVolume : Double.NaN;
    }

    public boolean ready() {
        return totalVolume != 0;
    }

    /** Forgets everything added, as at the start of a new session. */
    public void reset() {
        totalValue = 0;
        totalVolume = 0;
    }

    /** The VWAP of these candles by their closes. */
    public static double of(List<Candle> candles) {
        return of(candles, Candle::getCloseAsDouble);
    }

    /** The VWAP of these candles, the price of each taken by {@code priceExtractor}. */
    public static double of(List<Candle> candles, Function<Candle, Double> priceExtractor) {
        if (candles == null) {
            return Double.NaN;
        }

        Vwap vwap = new Vwap(priceExtractor);

        for (Candle candle : candles) {
            vwap.add(candle);
        }

        return vwap.value();
    }

    /** The reading at every one of these candles, {@link Double#NaN} until there has been some volume. */
    public static double[] seriesOf(List<Candle> candles, Function<Candle, Double> priceExtractor) {
        return seriesOf(candles, priceExtractor, false);
    }

    /**
     * The reading at every one of these candles, {@link Double#NaN} until there has been some volume.
     * With {@code resetDaily} the sums start over at the first candle of every UTC day, which is how a
     * session VWAP is read; without it they run from the first candle to the last.
     */
    public static double[] seriesOf(List<Candle> candles, Function<Candle, Double> priceExtractor, boolean resetDaily) {
        double[] readings = new double[candles.size()];
        Vwap vwap = new Vwap(priceExtractor);
        LocalDate day = null;

        for (int at = 0; at < candles.size(); at++) {
            Candle candle = candles.get(at);

            if (resetDaily) {
                LocalDate candleDay = candle.getTime().atZone(ZoneOffset.UTC).toLocalDate();

                if (!candleDay.equals(day)) {
                    vwap.reset();
                    day = candleDay;
                }
            }

            readings[at] = vwap.add(candle);
        }

        return readings;
    }
}
