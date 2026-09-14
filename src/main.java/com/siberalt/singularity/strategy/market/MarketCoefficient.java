package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Something measured about the market itself, read from the same candles a signal reads.
 * <p>
 * What it is for: which signal suits an instrument is not a property of the signal. A trend read
 * earns where moves carry on and loses where they come back, and which of the two a market is doing
 * can be measured without reference to any signal - see
 * {@link com.siberalt.singularity.strategy.analysis.VarianceRatio}. Calculators that choose between
 * readings, or weigh them, ask a coefficient rather than trying both and keeping whichever did
 * better on the data they were fitted to.
 * <p>
 * It has to be something the market is doing rather than something a signal thinks, or choosing by
 * it is only a more roundabout way of choosing by signal strength.
 */
@FunctionalInterface
public interface MarketCoefficient {
    double of(List<Candle> lastCandles);
}
