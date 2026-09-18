package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The horizontal axis of a chart: one row per bar, numbered in order, whatever width the bars are.
 * <p>
 * Everything a chart is asked to draw arrives in one of two other coordinates - the index of the
 * candle it was computed on, which is what extremes and levels carry, or an instant, which is what
 * orders carry - and this is the one place both are turned into a row. That used to be done by each
 * series for itself, and the three ways it was done agreed only on minute candles, where a candle's
 * index, its position in the list and its row are the same number. Rolled up into hours they are
 * not: an hour starts sixty indices after the last one, or fifty, or one, and a price line, the
 * orders on it and the levels over it each landed somewhere different.
 * <p>
 * A row covers a run of indices and a span of time. Built from narrower candles, a bar covers
 * exactly the indices of the candles rolled into it and the whole of its bucket of the clock. Built
 * from bars as they are, a bar covers everything up to the next one; the last covers its own index
 * and as much time as the narrowest gap between two bars, which is the best guess at its width.
 */
public class BarAxis {
    private final List<Candle> bars;
    private final long[] firstIndices;
    private final long[] lastIndices;
    private final long[] starts;
    private final long[] ends;

    private BarAxis(List<Candle> bars, long[] firstIndices, long[] lastIndices, long[] starts, long[] ends) {
        this.bars = List.copyOf(bars);
        this.firstIndices = firstIndices;
        this.lastIndices = lastIndices;
        this.starts = starts;
        this.ends = ends;
    }

    /**
     * Rolls candles up into bars of {@code interval} and lays the bars out one per row.
     *
     * @param candles  ordered oldest first, one instrument, each narrower than or as wide as the interval
     */
    public static BarAxis of(List<Candle> candles, CandleInterval interval) {
        CandleAggregator aggregator = new CandleAggregator();
        long width = interval.getDuration().toMillis();
        List<Candle> bars = new ArrayList<>();
        List<long[]> coverage = new ArrayList<>();
        List<Candle> bucket = new ArrayList<>();
        long current = Long.MIN_VALUE;

        for (Candle candle : candles) {
            long bucketOf = aggregator.bucketOf(candle, interval);

            if (bucketOf != current && !bucket.isEmpty()) {
                bars.add(aggregator.merge(bucket));
                coverage.add(coverageOf(bucket, current, width));
                bucket = new ArrayList<>();
            }

            current = bucketOf;
            bucket.add(candle);
        }

        if (!bucket.isEmpty()) {
            bars.add(aggregator.merge(bucket));
            coverage.add(coverageOf(bucket, current, width));
        }

        return new BarAxis(
            bars,
            coverage.stream().mapToLong(covered -> covered[0]).toArray(),
            coverage.stream().mapToLong(covered -> covered[1]).toArray(),
            coverage.stream().mapToLong(covered -> covered[2]).toArray(),
            coverage.stream().mapToLong(covered -> covered[3]).toArray()
        );
    }

    /** Lays bars out one per row as they are, each covering everything up to the next. */
    public static BarAxis ofBars(List<Candle> bars) {
        int size = bars.size();
        long[] firstIndices = new long[size];
        long[] lastIndices = new long[size];
        long[] starts = new long[size];
        long[] ends = new long[size];
        long narrowestGap = Long.MAX_VALUE;

        for (int row = 0; row < size; row++) {
            firstIndices[row] = bars.get(row).getIndex();
            starts[row] = bars.get(row).getTime().toEpochMilli();

            if (row > 0) {
                narrowestGap = Math.min(narrowestGap, Math.max(1, starts[row] - starts[row - 1]));
            }
        }

        for (int row = 0; row < size; row++) {
            boolean last = row == size - 1;
            lastIndices[row] = last ? firstIndices[row] : Math.max(firstIndices[row], firstIndices[row + 1] - 1);
            ends[row] = last ? starts[row] + (narrowestGap == Long.MAX_VALUE ? 1 : narrowestGap) : starts[row + 1];
        }

        return new BarAxis(bars, firstIndices, lastIndices, starts, ends);
    }

    private static long[] coverageOf(List<Candle> bucket, long bucketNumber, long width) {
        return new long[]{
            bucket.getFirst().getIndex(),
            bucket.getLast().getIndex(),
            bucketNumber * width,
            bucketNumber * width + width
        };
    }

    public int size() {
        return bars.size();
    }

    public boolean isEmpty() {
        return bars.isEmpty();
    }

    public List<Candle> bars() {
        return bars;
    }

    public Candle bar(int row) {
        return bars.get(row);
    }

    /** The index of the first candle rolled into the bar - what a function of the index is read at. */
    public long indexAt(int row) {
        return firstIndices[row];
    }

    /**
     * The row whose bar covers this index; {@code -1} before the first bar and {@link #size()} after
     * the last, so a caller can tell which edge it fell off.
     */
    public int rowOfIndex(long index) {
        if (bars.isEmpty() || index < firstIndices[0]) {
            return -1;
        }

        if (index > lastIndices[bars.size() - 1]) {
            return bars.size();
        }

        return floor(firstIndices, index);
    }

    /**
     * The row of the bar this instant falls in, or of the last bar before it when it falls between
     * two - a quiet night belongs to the evening before it. {@code -1} before the first bar and
     * {@link #size()} after the last.
     */
    public int rowOfTime(Instant time) {
        long millis = time.toEpochMilli();

        if (bars.isEmpty() || millis < starts[0]) {
            return -1;
        }

        if (millis >= ends[bars.size() - 1]) {
            return bars.size();
        }

        return floor(starts, millis);
    }

    /** The last position whose value is at most {@code key}; the values must not decrease. */
    private static int floor(long[] values, long key) {
        int low = 0;
        int high = values.length - 1;

        while (low < high) {
            int middle = (low + high + 1) >>> 1;

            if (values[middle] <= key) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }

        return low;
    }

    /** How many rows of data a chart of this axis has at this step. */
    public int rowsAt(long stepInterval) {
        return bars.isEmpty() ? 0 : (int) ((bars.size() - 1) / stepInterval + 1);
    }
}
