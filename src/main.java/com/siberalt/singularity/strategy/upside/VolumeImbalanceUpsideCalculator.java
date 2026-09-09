package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Which side of the market was taking liquidity, averaged over the recent bars and measured against
 * how lopsided this instrument's flow usually gets: buyers lifting offers reads positive, sellers
 * hitting bids negative.
 * <p>
 * The one thing here that is not derived from the price series. Every trend, level and volatility
 * measure in this package is a rearrangement of the same closes, so they can only ever disagree
 * about how to read one number; who traded against whom is a separate observation, and it is the
 * only place a signal uncorrelated with the others can come from. Measured on hourly bars of one
 * share it kept two thirds of its predictive power after the slope's contribution was taken out.
 * <p>
 * The reading is normalised against the instrument's own history rather than left as a raw share,
 * for the same reason {@link SlopeUpsideCalculator} divides its slope by the average move: a raw
 * imbalance averaged over thirty bars almost never approaches one, so a strategy comparing it to a
 * threshold near one would never trade, and the same threshold would mean something different on
 * every instrument. Normalised, one means as one-sided as this instrument usually gets, and the
 * threshold carries the meaning it was set with.
 * <p>
 * Bars with no split recorded are skipped rather than counted as balanced. The feed only began
 * reporting it partway through, and a stretch of history without it would otherwise read as a
 * market in perfect equilibrium - a strong claim to make from missing data.
 */
public class VolumeImbalanceUpsideCalculator implements UpsideCalculator {
    /**
     * Bars averaged over. Short enough to be about now rather than about the week, long enough that
     * one bar of someone's large order does not become the signal.
     */
    public static final int DEFAULT_PERIOD = 10;

    private final int period;

    public VolumeImbalanceUpsideCalculator() {
        this(DEFAULT_PERIOD);
    }

    public VolumeImbalanceUpsideCalculator(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Period must be at least one bar, got " + period);
        }

        this.period = period;
    }

    /**
     * @param lastCandles the recent {@code period} bars carry the reading; everything before them
     *                    is what it is measured against, so the window has to be a good deal longer
     *                    than the period or there is nothing to compare with
     */
    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < 2 * period) {
            return Upside.NEUTRAL;
        }

        int size = lastCandles.size();
        long[] net = new long[size + 1];
        long[] traded = new long[size + 1];
        int withSplit = 0;

        for (int bar = 0; bar < size; bar++) {
            Candle candle = lastCandles.get(bar);
            boolean hasSplit = candle.hasVolumeSplit();
            net[bar + 1] = net[bar] + (hasSplit ? candle.netVolume() : 0);
            traded[bar + 1] = traded[bar] + (hasSplit ? candle.tradedVolume() : 0);

            if (hasSplit && bar >= size - period) {
                withSplit++;
            }
        }

        Double current = imbalanceEndingAt(net, traded, size - 1);

        if (current == null) {
            return Upside.NEUTRAL;
        }

        double scale = typicalImbalance(net, traded, size);

        if (scale <= 0) {
            return Upside.NEUTRAL;
        }

        return new Upside(Math.tanh(current / scale), (double) withSplit / period);
    }

    /**
     * How lopsided this instrument's flow usually is, over the part of the window the reading does
     * not cover. The same statistic the reading is - an imbalance over {@code period} bars - and not
     * the imbalance of a single bar, which is larger by roughly the square root of the period and
     * would leave every reading looking mild.
     */
    protected double typicalImbalance(long[] net, long[] traded, int size) {
        double sum = 0;
        int count = 0;

        for (int end = period - 1; end < size - period; end++) {
            Double imbalance = imbalanceEndingAt(net, traded, end);

            if (imbalance == null) {
                continue;
            }

            sum += Math.abs(imbalance);
            count++;
        }

        return count == 0 ? 0 : sum / count;
    }

    /** The imbalance over the {@code period} bars ending at {@code end}, or null where none traded. */
    protected Double imbalanceEndingAt(long[] net, long[] traded, int end) {
        int from = end - period + 1;
        long tradedInWindow = traded[end + 1] - traded[from];

        return tradedInWindow == 0 ? null : (double) (net[end + 1] - net[from]) / tradedInWindow;
    }
}
