package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls a run of candles up into wider ones: the open of the first, the close of the last, the
 * extremes and the volume of all of them.
 * <p>
 * Bars are bucketed by the wall clock rather than counted off, so a gap in the data leaves a gap in
 * the result instead of shifting every bar after it into the wrong period.
 * <p>
 * <b>A rolled-up bar is numbered by its own bucket, not by the first narrow candle in it.</b> The
 * number is {@link #bucketOf}, which is what the bucketing already computes, so it is the same for a
 * bar whichever side it is assembled from - whole in {@link #aggregate} or one at a time through
 * {@link #merge} - and the same in every window it appears in. That stability is what the index is
 * for: {@link com.siberalt.singularity.strategy.extreme.cache.CachingExtremeLocator} keys the
 * stretches it has already scanned by the index of their first and last bar, across calls.
 * <p>
 * Keeping the narrow candle's index instead, as this used to, put the wide bar in the narrow series'
 * coordinates, where the step between two neighbouring wide bars is the length of a session - three
 * hundred and sixty of them after a short day, five hundred and forty after a full one. Anything
 * measuring a distance in bars had to average that out: {@link BarSpacing} did, and the reach of
 * {@link com.siberalt.singularity.strategy.extreme.ProximityGroupingExtremeLocator} was slack by a
 * day or two because of it.
 * <p>
 * Two things the new number is not. It is <b>not dense</b>: a stretch with no trading in it is a step
 * of more than one, so a position in a list is still something to search for rather than subtract.
 * Rarely, as it turns out - of the 1878 steps between this base's daily bars for one share, 1791 are
 * exactly one and the average is 1.108, because this base holds weekend sessions too - Sber's go back
 * to 2021-02-20, not to 2025 as this said before it was counted - but
 * rarely is not never, and the 87 that are not would land on the wrong bar. And it is
 * <b>not the database's numbering</b>, which is a row number per instrument; this one counts buckets
 * of wall-clock time, so the same day carries the same number for every instrument, which per-
 * instrument row numbers never gave.
 * <p>
 * The bar's <b>time</b> is left as the first narrow candle's, not the start of the bucket: the clock
 * a simulation runs on is read from it, and moving it would move the whole run.
 */
public class CandleAggregator {
    /**
     * @param candles  ordered oldest first, all of one instrument
     * @param interval the width to roll up to; every bar of {@code candles} must be narrower
     * @throws IllegalArgumentException for an interval with no fixed width - a month is not a
     *                                  number of milliseconds, and bucketing by one would drift
     */
    public List<Candle> aggregate(List<Candle> candles, CandleInterval interval) {
        requireFixedWidth(interval);

        List<Candle> result = new ArrayList<>();
        List<Candle> bucket = new ArrayList<>();
        long currentBucket = Long.MIN_VALUE;

        for (Candle candle : candles) {
            long candleBucket = bucketOf(candle, interval);

            if (candleBucket != currentBucket) {
                if (!bucket.isEmpty()) {
                    result.add(merge(bucket, interval));
                }

                bucket = new ArrayList<>();
                currentBucket = candleBucket;
            }

            bucket.add(candle);
        }

        if (!bucket.isEmpty()) {
            result.add(merge(bucket, interval));
        }

        return result;
    }

    /**
     * Which bar of the wider interval this candle falls in. Two candles share a bucket exactly when
     * they belong to the same wider bar.
     */
    public long bucketOf(Candle candle, CandleInterval interval) {
        requireFixedWidth(interval);

        return Math.floorDiv(candle.getTime().toEpochMilli(), interval.getDuration().toMillis());
    }

    /**
     * Rolls a run of candles known to belong together into the single candle they make up.
     * <p>
     * The interval is not optional, and not only to number the bar: a run of candles does not say how
     * wide the bar it makes up is, and the one thing a caller could not supply - the bucket number -
     * is the one thing that places the bar in the right series.
     *
     * @param bucket   candles of one bucket, ordered oldest first
     * @param interval the width of the bar they make up
     */
    public Candle merge(List<Candle> bucket, CandleInterval interval) {
        Candle first = bucket.getFirst();
        Candle last = bucket.getLast();
        Quotation high = first.high();
        Quotation low = first.low();
        long volume = 0;
        long volumeBuy = 0;
        long volumeSell = 0;

        for (Candle candle : bucket) {
            if (candle.high().isGreaterThan(high)) {
                high = candle.high();
            }

            if (candle.low().isLessThan(low)) {
                low = candle.low();
            }

            volume += candle.volume();
            volumeBuy += candle.volumeBuy();
            volumeSell += candle.volumeSell();
        }

        return new Candle(
            first.instrumentId(),
            new TimePoint(bucketOf(first, interval), first.getTime()),
            first.open(),
            last.close(),
            high,
            low,
            volume,
            volumeBuy,
            volumeSell
        );
    }

    protected void requireFixedWidth(CandleInterval interval) {
        if (interval == CandleInterval.UNSPECIFIED || interval == CandleInterval.MONTH) {
            throw new IllegalArgumentException("Cannot bucket by " + interval + ": it has no fixed width");
        }
    }
}
