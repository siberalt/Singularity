package com.siberalt.singularity.strategy.impl.quantity;

/**
 * What the account could do with this instrument at the moment of the decision, in lots.
 * <p>
 * Both numbers are given to both sides of {@link TradeQuantity}, not one each, because a sizing
 * that thinks in terms of a target position needs them together: how much to buy depends on how
 * much is already held, and how much to sell depends on how much the account is worth. Handing
 * {@code toBuy} only what it can afford makes every buy an increment onto whatever came before,
 * and a strategy sized that way keeps adding for as long as its signal holds - all the way up a
 * move it meant to catch the start of.
 *
 * @param affordableLots the most the account could buy at the current price, commission included
 * @param positionLots   what the account currently holds
 */
public record TradeCapacity(long affordableLots, long positionLots) {
    /**
     * The account's whole worth in lots of this instrument: what it holds plus what the rest of its
     * money would buy. Approximate by construction - the two halves are counted at slightly
     * different prices, one of them net of commission - but it is the size a fully committed
     * position would be, which is what a target is a share of.
     */
    public long totalLots() {
        return affordableLots + positionLots;
    }

    public static TradeCapacity of(long affordableLots, long positionLots) {
        return new TradeCapacity(affordableLots, positionLots);
    }
}
