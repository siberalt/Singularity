package com.siberalt.singularity.strategy.impl.quantity;

/**
 * How many lots a strategy asks for when it decides to trade.
 * <p>
 * Worth separating from the decision to trade at all, because the two answer to different things.
 * Whether to buy is about the signal; how much to buy is about what the market can absorb without
 * the order becoming the market - and a size that looked fine against a backtest with unlimited
 * liquidity is the first thing to go wrong once the simulation stops pretending.
 *
 * @see SignalScaledQuantity the plain answer - as much as the account allows, scaled by conviction
 * @see AdvCappedQuantity     the same, held down to a share of what the instrument actually trades
 */
public interface TradeQuantity {
    /**
     * @param affordableLots the most the account could buy at the current price
     */
    long toBuy(TradeMoment moment, long affordableLots);

    /**
     * @param positionLots what the account currently holds
     */
    long toSell(TradeMoment moment, long positionLots);
}
