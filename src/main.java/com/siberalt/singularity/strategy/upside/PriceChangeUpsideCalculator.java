package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;

/**
 * How far the price has moved over the last so many candles, against a threshold for each side.
 * <p>
 * The plainest way to ask whether something unusual has happened, and the one that measured best: a move
 * of a given size in per cent, not a slope and not a count of ATRs. Normalising by the instrument's own
 * volatility was tried on the same events and came out worse - a big move on a wild name is its ordinary
 * Tuesday, while five per cent in ten minutes is five per cent for everyone, and it is the absolute size
 * of the dislocation that comes back.
 * <p>
 * The signal is the direction of the move itself: {@code +1} when the price has risen past the rise
 * threshold, {@code -1} when it has fallen past the fall threshold, and {@link Upside#NEUTRAL} in between.
 * That is the move, not a bet on it. What was measured here is that such moves come back rather than
 * carry on, so a strategy meaning to trade them wants {@link InvertedUpsideCalculator} around this one -
 * buying the fall and selling the rise. Reading it the other way round loses by the same amount.
 * <p>
 * The window is counted in candles, not in wall-clock time, so a window reaching past the close spans the
 * night; that is how the rest of the calculators see their windows and how it was measured.
 * <p>
 * The thresholds are separate because the two sides are not mirrors: the fall pays from about five per
 * cent and the rise needs seven before it covers the cost of trading it.
 */
public class PriceChangeUpsideCalculator implements UpsideCalculator {
    private final int period;

    private final double risePercent;

    private final double fallPercent;

    private PriceExtractor priceExtractor = Candle::close;

    /**
     * @param period      how many candles back the move is measured over
     * @param risePercent how far up it has to have gone to say so, in per cent
     * @param fallPercent and how far down, as a positive number
     */
    public PriceChangeUpsideCalculator(int period, double risePercent, double fallPercent) {
        if (period < 1) {
            throw new IllegalArgumentException("A move needs at least one candle to be measured over");
        }

        if (risePercent <= 0 || fallPercent <= 0) {
            throw new IllegalArgumentException("Both thresholds must be above zero");
        }

        this.period = period;
        this.risePercent = risePercent;
        this.fallPercent = fallPercent;
    }

    /** The same threshold both ways. */
    public PriceChangeUpsideCalculator(int period, double percent) {
        this(period, percent, percent);
    }

    /** Reads the move off something other than the close - the highs, say. */
    public PriceChangeUpsideCalculator setPriceExtractor(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;

        return this;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < period + 1) {
            return Upside.NEUTRAL;
        }

        double before = priceExtractor.extract(lastCandles.get(lastCandles.size() - 1 - period)).toDouble();
        double now = priceExtractor.extract(lastCandles.getLast()).toDouble();

        if (before <= 0 || now <= 0) {
            return Upside.NEUTRAL;
        }

        double moved = 100 * (now / before - 1);

        if (moved >= risePercent) {
            return new Upside(1, 1);
        }

        if (moved <= -fallPercent) {
            return new Upside(-1, 1);
        }

        return Upside.NEUTRAL;
    }
}
