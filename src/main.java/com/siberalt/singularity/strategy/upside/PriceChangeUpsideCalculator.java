package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;

/**
 * How far the price has moved over the last so many candles, measured from the extreme of the window and
 * only while the window still ends on its own extreme.
 * <p>
 * A move of a given size in per cent is the plainest way to ask whether something unusual has happened,
 * and the one that measured best: not a slope, and not a count of ATRs. Normalising by the instrument's
 * own volatility was tried on the same events and came out worse - a big move on a wild name is its
 * ordinary Tuesday, while five per cent in ten minutes is five per cent for everyone, and it is the
 * absolute size of the dislocation that comes back.
 * <p>
 * Where the move is measured from and until when it still counts are two separate questions, and both are
 * answered here by the window's own extremes rather than by its first candle:
 * <ul>
 *   <li><b>The rise is measured from the lowest price in the window</b>, the fall from the highest. The
 *   first candle is an arbitrary point that happens to be {@code period} bars back; the low is where the
 *   move being asked about actually began, and anchoring there stops the same move reading as smaller
 *   merely because the window opened after it had started.</li>
 *   <li><b>Only the part of the window from that extreme onwards is looked at</b>, and that part has to
 *   end on its own extreme: for a rise, no candle after the low may stand above the last one. A candle
 *   above the end means the top of this move has already been printed and what is left is its aftermath,
 *   which is a different event from a move sitting at its high. A high standing <i>before</i> the low is
 *   another matter and is ignored - it belongs to whatever the price was doing before this move began, and
 *   letting it veto the signal would throw away the move itself.</li>
 * </ul>
 * The two conditions pull opposite ways: anchoring at the extreme makes the reading larger and fires more
 * often, while requiring the significant part to end on its extreme fires less. What the pair of them is
 * worth against the plain reading between two candles is a matter for measurement, not for argument.
 * <p>
 * The signal is the direction of the move itself: {@code +1} when the price has risen past the rise
 * threshold, {@code -1} when it has fallen past the fall threshold, and {@link Upside#NEUTRAL} in between.
 * That is the move, not a bet on it. What was measured here is that such moves come back rather than
 * carry on, so a strategy meaning to trade them wants {@link InvertedUpsideCalculator} around this one -
 * buying the fall and selling the rise. Reading it the other way round loses by the same amount.
 * <p>
 * The window is counted in candles, not in wall-clock time, so a window reaching past the close spans the
 * night; that is how the rest of the calculators see their windows and how it was measured. Everything -
 * both extremes and the end - is read through the same {@link #setPriceExtractor extractor}, so asking for
 * the highs compares highs with highs.
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

        int last = lastCandles.size() - 1;
        double now = priceExtractor.extract(lastCandles.get(last)).toDouble();

        if (now <= 0) {
            return Upside.NEUTRAL;
        }

        double rise = movedFrom(lastCandles, last, now, true);

        if (!Double.isNaN(rise) && rise >= risePercent) {
            return new Upside(1, 1);
        }

        double fall = movedFrom(lastCandles, last, now, false);

        if (!Double.isNaN(fall) && fall <= -fallPercent) {
            return new Upside(-1, 1);
        }

        return Upside.NEUTRAL;
    }

    /**
     * The move over the significant part of the window: from its extreme to its end, in per cent, or
     * {@link Double#NaN} when that part no longer ends on an extreme of its own.
     * <p>
     * One backward walk does both halves of the reading. The extreme is the window's lowest price for a
     * rise, and the part that matters is everything after it - what the price did before it belongs to
     * whatever move ended there and says nothing about this one. A high standing before the low is
     * therefore ignored, while a high standing after it is decisive: it means the top of this move has
     * already been printed and what is left of the window is its aftermath.
     * <p>
     * When the lowest price occurs more than once, the last of them starts the part that matters, which is
     * the shortest honest reading of it. The fall is the same thing upside down.
     *
     * @param rise whether the move being read is up, in which case the extreme is the window's lowest
     *             price and the part after it may hold nothing above the end
     */
    private double movedFrom(List<Candle> lastCandles, int last, double now, boolean rise) {
        double extreme = rise ? Double.MAX_VALUE : 0;
        // The furthest the price went the other way within the part that matters, and the same over
        // everything walked so far - which is that part as of the candle before the one being looked at.
        double beyond = rise ? 0 : Double.MAX_VALUE;
        double beyondSoFar = rise ? 0 : Double.MAX_VALUE;

        for (int at = last - 1; at >= last - period; at--) {
            double price = priceExtractor.extract(lastCandles.get(at)).toDouble();

            if (price <= 0) {
                return Double.NaN;
            }

            if (rise ? price < extreme : price > extreme) {
                extreme = price;
                beyond = beyondSoFar;
            }

            beyondSoFar = rise ? Math.max(beyondSoFar, price) : Math.min(beyondSoFar, price);
        }

        if (rise ? beyond > now : beyond < now) {
            return Double.NaN;
        }

        return 100 * (now / extreme - 1);
    }
}
