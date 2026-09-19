package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.BarSpacing;
import com.siberalt.singularity.entity.candle.Candle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collapses extremes that sit close together into the most extreme of them. Whatever the wrapped
 * locator reports, a run of extremes each within {@code area} bars of the one before is one turn of
 * the market, and only its best candle is kept.
 * <p>
 * A decorator rather than a step inside the locator that finds the extremes, because the two do not
 * compose the same way. Finding a pivot is local - a candle is one or it is not, judged by its
 * vicinity alone - so a series can be scanned in pieces and the pieces put together. Grouping is
 * not: a run can chain across any boundary, and which candle wins depends on the whole run. A cache
 * that scanned in pieces and grouped each piece kept one winner per piece where a single scan keeps
 * one per run - on two months of minute bars, half as many extremes again as there were. Laid over
 * the cache instead, grouping sees each window whole on every call and agrees with a plain scan.
 * <p>
 * The reach is meant in bars, not in index units. The two agree for raw candles, whose index is a
 * row number, and part company as soon as bars are rolled up: an hourly bar keeps the index of the
 * minute it opened on, so a reach of ten units grouped nothing at all. Bars are turned into units
 * by the window's average spacing, once per call. That is exact for raw candles and approximate for
 * rolled-up ones, whose hours hold uneven numbers of minutes - which is enough for deciding what is
 * one turn of the market. Finding each extreme's exact place among the bars instead cost a search
 * per extreme, and over a cache of dense pivots that was most of the time a call took.
 * <p>
 * The comparator is the one the wrapped locator judges by: of two candles the lesser is the more
 * extreme - lower for minimums, higher for maximums - and the least of a run is what survives. Of
 * equals, the first.
 */
public class ProximityGroupingExtremeLocator implements ExtremeLocator {
    private final ExtremeLocator extremeLocator;
    private final Comparator<Candle> comparator;
    private final int area;

    public ProximityGroupingExtremeLocator(ExtremeLocator extremeLocator, Comparator<Candle> comparator, int area) {
        if (extremeLocator == null || comparator == null) {
            throw new IllegalArgumentException("Grouping needs extremes to group and a way to rank them");
        }

        if (area < 0) {
            throw new IllegalArgumentException("A reach cannot be negative, got " + area);
        }

        this.extremeLocator = extremeLocator;
        this.comparator = comparator;
        this.area = area;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        return group(extremeLocator.locate(candles), candles, comparator, area);
    }

    /**
     * Keeps the most extreme candle of every run of extremes whose neighbours sit at most
     * {@code area} bars apart.
     *
     * @param extremes ordered oldest first
     * @param candles  the bars they were found among, ordered oldest first
     */
    public static List<Candle> group(List<Candle> extremes, List<Candle> candles, Comparator<Candle> comparator, int area) {
        if (area == 0 || extremes.size() < 2) {
            return extremes;
        }

        double reach = area * BarSpacing.of(candles);
        List<Candle> kept = new ArrayList<>();
        Candle best = extremes.getFirst();

        for (int i = 1; i < extremes.size(); i++) {
            Candle extreme = extremes.get(i);

            if (extreme.getIndex() - extremes.get(i - 1).getIndex() > reach) {
                kept.add(best);
                best = extreme;
            } else if (comparator.compare(extreme, best) < 0) {
                best = extreme;
            }
        }

        kept.add(best);

        return kept;
    }

}
