package com.siberalt.singularity.strategy.signal;

/**
 * What a {@link SignalSource} makes of the market: which way, how sure, and how big a reading it was.
 *
 * @param confidence which way to act and how strongly, in [-1, 1]. The sign is the side - above zero is
 *                   a buy, below is a sell - and the magnitude is how sure the source is. A source with
 *                   nothing to add about degree reports ±1 and leaves it at that; one that measures its
 *                   own conviction reports a fraction, and a strategy's buy and sell thresholds are read
 *                   against exactly this number. Sizing reads it too: how much of the account to commit
 *                   follows from how sure the signal is, which is why this is confidence rather than a
 *                   bare direction.
 * @param strength   the quantity the source measured, in <b>the source's own units</b> - not a second
 *                   confidence. {@code |MACD| / price} for the MACD trend, the volume's angle for
 *                   {@link ChangeAngleSignalSource}, the momentum gap for a divergence. Comparable
 *                   between instruments where the source says so and between sources not at all, which
 *                   is why a threshold on it belongs next to the source that produced it - see
 *                   {@link com.siberalt.singularity.strategy.signal.condition.Deadband}.
 */
public record Signal(double confidence, double strength) {
    public static final Signal NEUTRAL = new Signal(0, 0);
}
