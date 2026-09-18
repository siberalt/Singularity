package com.siberalt.singularity.presenter.google.series;

import java.util.Optional;

public interface SeriesProvider {
    /**
     * The series laid out one row per bar of the axis, every {@code stepInterval}-th bar a row of
     * data. Whatever the series is addressed in - a candle index, an instant - the axis is what
     * turns it into a row, which is the only way the layers of a chart end up in the same row as
     * the bar they belong to once bars are wider than the candles underneath them.
     */
    Optional<SeriesChunk> provide(BarAxis axis, long stepInterval);

    /** The data row a bar lands in: the nearest multiple of the step, kept inside the chart. */
    static int dataRowOf(int barRow, long stepInterval, int rows) {
        long remainder = barRow % stepInterval;
        long rounded = remainder <= stepInterval / 2 ? barRow - remainder : barRow - remainder + stepInterval;

        return (int) Math.min(rounded / stepInterval, rows - 1);
    }
}
