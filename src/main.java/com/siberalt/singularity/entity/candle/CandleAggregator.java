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
 */
public class CandleAggregator {
    /**
     * @param candles  ordered oldest first, all of one instrument
     * @param interval the width to roll up to; every bar of {@code candles} must be narrower
     * @throws IllegalArgumentException for an interval with no fixed width - a month is not a
     *                                  number of milliseconds, and bucketing by one would drift
     */
    public List<Candle> aggregate(List<Candle> candles, CandleInterval interval) {
        if (interval == CandleInterval.UNSPECIFIED || interval == CandleInterval.MONTH) {
            throw new IllegalArgumentException("Cannot bucket by " + interval + ": it has no fixed width");
        }

        long bucketMillis = interval.getDuration().toMillis();
        List<Candle> result = new ArrayList<>();
        List<Candle> bucket = new ArrayList<>();
        long currentBucket = Long.MIN_VALUE;

        for (Candle candle : candles) {
            long candleBucket = Math.floorDiv(candle.getTime().toEpochMilli(), bucketMillis);

            if (candleBucket != currentBucket) {
                if (!bucket.isEmpty()) {
                    result.add(merge(bucket));
                }

                bucket = new ArrayList<>();
                currentBucket = candleBucket;
            }

            bucket.add(candle);
        }

        if (!bucket.isEmpty()) {
            result.add(merge(bucket));
        }

        return result;
    }

    protected Candle merge(List<Candle> bucket) {
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
            first.instrumentUid(),
            first.timePoint(),
            first.open(),
            last.close(),
            high,
            low,
            volume,
            volumeBuy,
            volumeSell
        );
    }
}
