package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Something the market either is or is not doing, read from the same candles a signal reads.
 * <p>
 * The yes-or-no sibling of {@link MarketCoefficient}, and it exists for the same reason: whether a signal
 * is worth acting on at all can depend on the state of the market, and that state is measurable without
 * reference to the signal. A session that has barely traded, a spread too wide to cross, an hour the
 * instrument is known not to move in - each is a fact about the market, and
 * {@link com.siberalt.singularity.strategy.upside.FilterUpsideCalculator} turns any of them into a veto.
 * <p>
 * As with a coefficient, it has to be something the market is doing rather than something the signal
 * thinks. A condition that reads the signal's own strength is not a filter but a second threshold on it,
 * and calling it a filter hides that.
 */
@FunctionalInterface
public interface MarketCondition {
    boolean holds(List<Candle> lastCandles);
}
