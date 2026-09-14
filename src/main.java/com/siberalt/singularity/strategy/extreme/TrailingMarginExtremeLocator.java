package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Drops the extremes a window cannot yet be sure of: everything within {@code margin} bars of its
 * end.
 * <p>
 * A locator that calls a candle an extreme by comparing it with its neighbours cannot judge the
 * candles at the very end of what it is given, the bars that would settle them not having arrived.
 * Scanning a window whole it simply reports nothing there, and that is the end of the matter. A
 * cache is different: it keeps what it found, and some window that ended further along has settled
 * those candles already. Alone with a cache a caller never meets them, its windows only ever moving
 * forward. Sharing one with threads at other points of the same history, it does - and an extreme
 * confirmed by bars this window has not reached is look-ahead wearing the clothes of memory.
 * <p>
 * A filter rather than a question every locator has to answer, because how much is unsettled is a
 * property of the locator that found the extremes, and the composition root is what knows which
 * locator that was.
 * <p>
 * Where it goes in a chain matters: over the cache, and under whatever groups the extremes. A plain
 * scan never offers its unsettled tail to the grouping either, and a group that took its winner from
 * that tail would answer differently.
 */
public class TrailingMarginExtremeLocator implements ExtremeLocator {
    private final ExtremeLocator extremeLocator;
    private final int margin;

    public TrailingMarginExtremeLocator(ExtremeLocator extremeLocator, int margin) {
        if (extremeLocator == null) {
            throw new IllegalArgumentException("Nothing to take a margin off");
        }

        if (margin < 0) {
            throw new IllegalArgumentException("A margin cannot be negative, got " + margin);
        }

        this.extremeLocator = extremeLocator;
        this.margin = margin;
    }

    public int getMargin() {
        return margin;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        List<Candle> extremes = extremeLocator.locate(candles);

        if (margin == 0 || extremes.isEmpty()) {
            return extremes;
        }

        if (candles.size() <= margin) {
            return List.of();
        }

        long lastSettled = candles.get(candles.size() - 1 - margin).getIndex();

        return extremes.stream().filter(extreme -> extreme.getIndex() <= lastSettled).toList();
    }
}
