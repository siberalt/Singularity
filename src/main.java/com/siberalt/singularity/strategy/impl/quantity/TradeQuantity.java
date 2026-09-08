package com.siberalt.singularity.strategy.impl.quantity;

/**
 * How many lots a strategy asks for when it decides to trade.
 * <p>
 * Worth separating from the decision to trade at all, because the two answer to different things.
 * Whether to buy is about the signal; how much to buy is about what the market can absorb without
 * the order becoming the market - and a size that looked fine against a backtest with unlimited
 * liquidity is the first thing to go wrong once the simulation stops pretending.
 *
 * @see SignalScaledQuantity   the plain answer - as much as the account allows, scaled by conviction
 * @see TargetPositionQuantity the same conviction read as a position to hold rather than a step to take
 * @see AdvCappedQuantity      either of them, held down to a share of what the instrument trades
 */
public interface TradeQuantity {
    long toBuy(TradeMoment moment, TradeCapacity capacity);

    long toSell(TradeMoment moment, TradeCapacity capacity);
}
