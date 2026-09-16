package com.siberalt.singularity.broker.impl.decorator;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.market.position.EntryPrice;
import com.siberalt.singularity.strategy.market.position.EntryPriceCalculator;
import com.siberalt.singularity.strategy.market.position.PositionRiskCoefficient;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.List;

/**
 * How far a position has moved against itself from the best price it has seen, counted in
 * volatilities: a trailing stop written as a signal.
 * <p>
 * It answers about a position rather than about the market, which makes it unlike everything else
 * that produces an {@link Upside}. With nothing open it says nothing. Holding something, it asks
 * {@link PositionRiskCoefficient} how far under water the position is and says to close that much of
 * it: a long reads negative as the price falls back from its reference, a full close signal one
 * volatility below it, and a short the other way round.
 * <p>
 * The reading is kept within a single volatility either way, and that clamp is the whole of what this
 * class adds. A strength is a confidence and every other calculator in the package reports one
 * between zero and one, so a position eight volatilities under water must not report a confidence of
 * eight - anything averaging strengths would take that literally.
 * <p>
 * The clamp is also why it cannot be the whole answer. Past one volatility every reading is the same
 * reading, so bolted into a threshold switch it can only ever say "all of it, now". A calculator that
 * wants to scale a position down as the risk grows should weigh
 * {@link PositionRiskCoefficient} itself - see
 * {@link com.siberalt.singularity.strategy.upside.SmoothSwitchUpsideCalculator} - and keep this one
 * for the verdict at the end of that scale.
 * <p>
 * What it is worth is not a question of prediction and cannot be settled the way a signal's is: the
 * thing to measure is what it does to the drawdown of a strategy it is bolted onto.
 */
public class PositionRiskManagerUpsideCalculator implements UpsideCalculator {
    private final PositionRiskCoefficient coefficient;

    /**
     * @param coefficient how far under water the position is, in volatilities - the reading this
     *                    calculator clamps into a signal
     */
    public PositionRiskManagerUpsideCalculator(PositionRiskCoefficient coefficient) {
        if (coefficient == null) {
            throw new IllegalArgumentException("A risk manager needs a reading to clamp");
        }

        this.coefficient = coefficient;
    }

    /**
     * @param volatilityCalculator how wide a volatility is - the units the reading is counted in
     * @param maxLocator           where a long's high water mark comes from
     * @param minLocator           where a short's low water mark comes from
     */
    public PositionRiskManagerUpsideCalculator(
        String accountId,
        String instrumentUid,
        EntryPriceCalculator entryPriceCalculator,
        VolatilityCalculator volatilityCalculator,
        ExtremeLocator maxLocator,
        ExtremeLocator minLocator,
        PriceExtractor priceExtractor
    ) {
        this(new PositionRiskCoefficient(
            accountId, instrumentUid, entryPriceCalculator, volatilityCalculator, maxLocator, minLocator, priceExtractor));
    }

    /** Defaults: an ATR of fourteen at a multiplier of one, close prices, extremes over a vicinity of three. */
    public PositionRiskManagerUpsideCalculator(String accountId, String instrumentUid, EntryPriceCalculator entryPriceCalculator) {
        this(new PositionRiskCoefficient(accountId, instrumentUid, entryPriceCalculator));
    }

    public PositionRiskManagerUpsideCalculator(String accountId,
                                               String instrumentUid,
                                               EntryPriceCalculator entryPriceCalculator,
                                               VolatilityCalculator volatilityCalculator
    ) {
        this(new PositionRiskCoefficient(accountId, instrumentUid, entryPriceCalculator, volatilityCalculator));
    }

    /**
     * The reading underneath, so that a calculator weighing the risk and this one closing on it work
     * off the same measurement rather than two configured separately and drifting apart.
     */
    public PositionRiskCoefficient getCoefficient() {
        return coefficient;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        EntryPrice position = coefficient.position();

        if (position.isEmpty() || position.quantity() == 0) {
            return Upside.NEUTRAL;
        }

        double underWater = coefficient.of(lastCandles);
        // Under water asks to close, and closing a long is a sell.
        double signal = position.quantity() > 0 ? -underWater : underWater;
        double clamped = Math.min(1, Math.max(-1, signal));

        // A position sitting exactly on its reference reads as nothing to say, and negating a zero
        // leaves a negative one that no longer equals NEUTRAL.
        return clamped == 0 ? Upside.NEUTRAL : new Upside(clamped, Math.abs(clamped));
    }
}
