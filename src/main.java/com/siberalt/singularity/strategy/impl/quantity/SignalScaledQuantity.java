package com.siberalt.singularity.strategy.impl.quantity;

/**
 * As much as the account allows, scaled by how strong the signal is: a full-conviction buy commits
 * the whole balance, a full-conviction sell closes the whole position.
 * <p>
 * The default, and the behaviour every backtest here was written against. It says nothing about
 * whether the market could absorb the result - against a thin instrument a balance-sized order is
 * many hours of that instrument's entire volume, which is a fact about the order that this sizing
 * has no way of noticing. {@link AdvCappedQuantity} wraps it to put a ceiling on that.
 */
public class SignalScaledQuantity implements TradeQuantity {
    @Override
    public long toBuy(TradeMoment moment, long affordableLots) {
        return (long) (affordableLots * moment.upside().signal());
    }

    @Override
    public long toSell(TradeMoment moment, long positionLots) {
        return (long) (positionLots * Math.abs(moment.upside().signal()));
    }
}
