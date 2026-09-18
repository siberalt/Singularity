package com.siberalt.singularity.presenter.google.series;

import java.util.Optional;

public interface SeriesProvider {
    Optional<SeriesChunk> provide(long start, long end, long stepInterval);

    /**
     * The series laid out one row per bar of the axis, every {@code stepInterval}-th bar a row of
     * data. A series whose points are already rows needs nothing more than the range of them; one
     * whose points are candle indices or instants places them through the axis, which is the only
     * way they end up in the same row as the bar they belong to once bars are wider than a minute.
     */
    default Optional<SeriesChunk> provide(BarAxis axis, long stepInterval) {
        if (axis.isEmpty()) {
            return Optional.empty();
        }

        return provide(0, axis.size() - 1, stepInterval);
    }

    /** The data row a bar lands in: the nearest multiple of the step, kept inside the chart. */
    static int dataRowOf(int barRow, long stepInterval, int rows) {
        long remainder = barRow % stepInterval;
        long rounded = remainder <= stepInterval / 2 ? barRow - remainder : barRow - remainder + stepInterval;

        return (int) Math.min(rounded / stepInterval, rows - 1);
    }
}
