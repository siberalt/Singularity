package com.siberalt.singularity.strategy.analysis;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;

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
    private PriceExtractor priceExtractor = Candle::getTypical;

    public PriceExtractor getPriceExtractor() {
        return priceExtractor;
    }

    /**
     * Which price of a bar to measure. The typical one by default, and that is not a detail: read
     * off closes, this measures the bid-ask bounce before it measures the market.
     * <p>
     * Consecutive closes land alternately on the bid and the ask, which is a move that always comes
     * back, so the ratio is dragged under one whatever the price is doing. On hourly bars of one
     * share it read 0.671 off closes against 0.992 off typical prices; on another, 1.024 against
     * 1.283. Window by window it was worse: the second share looked like a market that comes back in
     * seven windows out of ten off closes, and like one that carries on in seven out of ten off
     * typical prices - and a switch keyed on the first reading inverted a trend signal that worked,
     * turning a correlation of +0.16 into -0.10.
     */
    public VarianceRatio setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("A price has to come from somewhere");
        }

        this.priceExtractor = priceExtractor;
        return this;
    }

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
            double from = priceOf(candles.get(bar));
            double to = priceOf(candles.get(bar + step));
            returns[bar] = from > 0 && to > 0 ? Math.log(to / from) : 0;
        }

        return returns;
    }

    protected double priceOf(Candle candle) {
        Quotation price = priceExtractor.extract(candle);

        return price == null ? 0 : price.toDouble();
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
