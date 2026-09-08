package com.siberalt.singularity.strategy.impl.quantity;

/**
 * Sizes to the position the signal calls for, not to a step towards it: the strategy decides how
 * much of the account should be in the instrument, and trades the difference.
 * <p>
 * {@link SignalScaledQuantity} answers a different question - how much to add now - and answers it
 * the same way on every bar the signal is above the threshold. A signal that stays strong for two
 * hundred bars therefore buys two hundred times, each time with whatever the last purchase left
 * over, and the strategy that meant to buy the start of a move ends up buying all of it, at prices
 * that get worse the longer it is right. Limited liquidity makes it worse rather than better: it
 * spreads a single decision over many bars, so the accumulation reaches further up the move.
 * <p>
 * A target settles it without the strategy having to remember anything. The position it already
 * holds is the record of what it has done, and the difference between that and the target is all
 * there is left to trade. Once the target is met the same signal asks for nothing, and only a
 * stronger one asks for more. It also declines to chase on its own: as the price rises the money
 * left over buys fewer lots, so the target measured in lots falls.
 *
 * @see SignalScaledQuantity for the incremental sizing this replaces
 */
public class TargetPositionQuantity implements TradeQuantity {
    /**
     * A full-conviction signal commits the whole account. The reading that matches the incremental
     * sizing this replaces, and the one to move away from when the position turns out to be more
     * than the market will hand over while the signal still means anything.
     */
    public static final double DEFAULT_FULL_POSITION_SHARE = 1.0;

    private double fullPositionShare = DEFAULT_FULL_POSITION_SHARE;

    public TargetPositionQuantity(double fullPositionShare) {
        setFullPositionShare(fullPositionShare);
    }

    public TargetPositionQuantity() {
    }

    public double getFullPositionShare() {
        return fullPositionShare;
    }

    /**
     * How much of the account a signal of full conviction asks to hold.
     * <p>
     * The lever for fitting the position to the horizon of the signal. A position is not taken at
     * once - the market hands over a share of each bar and no more - so a target worth eighty bars
     * of that share is still being bought long after the bars the signal was read from have gone,
     * at prices it never saw. Sized to a few bars instead, the position is in place while the
     * reason for it is still true. It costs the upside of the part of the account left behind,
     * which is the trade being made.
     */
    public TargetPositionQuantity setFullPositionShare(double fullPositionShare) {
        if (fullPositionShare <= 0 || fullPositionShare > 1) {
            throw new IllegalArgumentException(
                String.format("Full position share must be within (0, 1], got %s", fullPositionShare)
            );
        }

        this.fullPositionShare = fullPositionShare;
        return this;
    }

    @Override
    public long toBuy(TradeMoment moment, TradeCapacity capacity) {
        return Math.max(0, targetLots(moment, capacity) - capacity.positionLots());
    }

    @Override
    public long toSell(TradeMoment moment, TradeCapacity capacity) {
        return Math.max(0, capacity.positionLots() - targetLots(moment, capacity));
    }

    /**
     * How much of the account the signal wants in the instrument, as a share of everything it could
     * commit. Read straight off the signal and clamped: full conviction is fully invested, and any
     * signal to sell is a target of nothing, which closes the position - the same thing the
     * incremental sizing did at full conviction, now said at every level of it - and then taken as
     * a share of {@link #getFullPositionShare()} rather than of everything, when the whole account
     * is more than the strategy means to commit to one instrument.
     */
    protected long targetLots(TradeMoment moment, TradeCapacity capacity) {
        double share = Math.clamp(moment.upside().signal(), 0d, 1d) * fullPositionShare;

        return (long) (capacity.totalLots() * share);
    }
}
