package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearRegression;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;

/**
 * How far the price has strayed from where it has been sitting, and which way it would come back:
 * above its recent average reads negative, below it positive.
 * <p>
 * The opposite bet to {@link SlopeUpsideCalculator}, and for the opposite instrument. Whether an
 * instrument's moves carry on or come back is a property of the instrument, not of the signal -
 * {@link com.siberalt.singularity.strategy.analysis.VarianceRatio} measures it, and of three shares
 * measured here one trended, one reverted and one did neither. A trend signal on the reverting one
 * lost more than half the account; the same signal inverted made money. This is that bet made
 * properly rather than by negation.
 * <p>
 * Measured against that inversion on the reverting share, this lost, and the measuring is worth
 * more than the class. The two differ in where the sign comes from: from the price's distance to
 * its average here, from the direction it was travelling there. On that share only the second knew
 * anything, which makes the working bet a statement about a move running out of steam rather than
 * about a price being far from home - and those are not the same claim, since a price can keep
 * walking away from its average long after the walk has turned.
 * <p>
 * The other suspected difference turned out not to be one. Inverting the slope also inherits its
 * goodness-of-fit gate, which was easy to read as a flaw - it selects for clean trends, and a
 * reversion bet has no business there. Adding the same gate here (see {@link #setMinStraightness})
 * roughly doubled what this earns per trade while cutting how often it fires by two thirds, which
 * is help rather than harm. It was not enough to close the gap, and the sample it left was too
 * small to be sure of either way.
 * <p>
 * The distance is measured in the spread of the window it strayed from, which is what makes it
 * comparable across instruments.
 * <p>
 * The default price is the typical one rather than the close, and that is not a detail. Close-to-
 * close returns on minute bars of the shares measured here had autocorrelation around minus a fifth
 * to minus two fifths - not because the price reverts, but because consecutive closes land
 * alternately on the bid and the ask. A reversion signal reading closes would find that bounce
 * before it found anything real, and would be reading a spread it cannot trade against. On the
 * typical price the same measurement came out near zero.
 */
public class MeanReversionUpsideCalculator implements UpsideCalculator {
    public static final int DEFAULT_PERIOD = 20;

    private final int period;
    private PriceExtractor priceExtractor = Candle::getTypical;
    private double minStraightness = 0;

    public MeanReversionUpsideCalculator() {
        this(DEFAULT_PERIOD);
    }

    public MeanReversionUpsideCalculator(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("Period must span at least two bars, got " + period);
        }

        this.period = period;
    }

    public PriceExtractor getPriceExtractor() {
        return priceExtractor;
    }

    /**
     * Which price of a bar to measure. The typical price by default - see the note on the bid-ask
     * bounce above before choosing the close.
     */
    public MeanReversionUpsideCalculator setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("A price has to come from somewhere");
        }

        this.priceExtractor = priceExtractor;
        return this;
    }

    public double getMinStraightness() {
        return minStraightness;
    }

    /**
     * How straight the departure has to have been before it is worth betting against, as the share
     * of the window's movement a straight line accounts for - the R squared of a line fitted
     * through it. Off by default, so every stray counts.
     * <p>
     * Worth having because of what turning {@link SlopeUpsideCalculator} round turned out to do. On
     * a reverting share that inversion beat this calculator handily, and the one thing it has that
     * this lacks is the slope's own goodness-of-fit gate: it fires only on clean, well-formed moves
     * and bets against their continuing. That is a narrower claim than reversion - not every stray
     * comes back, but a stray that arrived in a straight line may be a move that has run its course.
     * <p>
     * Set high, this and an inverted slope are close cousins, since a price that rose in a straight
     * line ends above its own average. What separates them is that the sign here comes from where
     * the price sits rather than from where it was going.
     */
    public MeanReversionUpsideCalculator setMinStraightness(double minStraightness) {
        if (minStraightness < 0 || minStraightness > 1) {
            throw new IllegalArgumentException(
                String.format("Straightness must be within [0, 1], got %s", minStraightness)
            );
        }

        this.minStraightness = minStraightness;
        return this;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < period) {
            return Upside.NEUTRAL;
        }

        double[] prices = pricesOf(lastCandles.subList(lastCandles.size() - period, lastCandles.size()));

        if (minStraightness > 0 && straightness(prices) < minStraightness) {
            return Upside.NEUTRAL;
        }

        double mean = mean(prices);
        double deviation = deviation(prices, mean);

        if (deviation == 0) {
            return Upside.NEUTRAL;
        }

        // Negative: a price above where it has been sitting is one to sell, not to buy.
        double strayed = (mean - prices[prices.length - 1]) / deviation;

        return new Upside(Math.tanh(strayed), crossingRate(prices, mean));
    }

    /**
     * How often the price crossed its own average over the window, as a share of the chances it
     * had. This is the confidence, because it is the property the whole bet rests on: a price that
     * keeps coming back has crossed often, and one that wandered off in a straight line has crossed
     * once or not at all - and a straight line is where a reversion bet loses.
     */
    protected double crossingRate(double[] prices, double mean) {
        int crossings = 0;

        for (int index = 1; index < prices.length; index++) {
            boolean wasAbove = prices[index - 1] > mean;
            boolean isAbove = prices[index] > mean;

            if (wasAbove != isAbove) {
                crossings++;
            }
        }

        return (double) crossings / (prices.length - 1);
    }

    /** How much of the window a straight line accounts for - the same measure the slope gates on. */
    protected double straightness(double[] prices) {
        double[] positions = new double[prices.length];

        for (int index = 0; index < positions.length; index++) {
            positions[index] = index;
        }

        return new LinearRegression(positions, prices).getR2();
    }

    protected double[] pricesOf(List<Candle> candles) {
        double[] prices = new double[candles.size()];

        for (int index = 0; index < prices.length; index++) {
            prices[index] = priceExtractor.extract(candles.get(index)).toDouble();
        }

        return prices;
    }

    protected double mean(double[] prices) {
        double sum = 0;

        for (double price : prices) {
            sum += price;
        }

        return sum / prices.length;
    }

    protected double deviation(double[] prices, double mean) {
        double sum = 0;

        for (double price : prices) {
            sum += (price - mean) * (price - mean);
        }

        return Math.sqrt(sum / (prices.length - 1));
    }
}
