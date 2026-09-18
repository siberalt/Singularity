package com.siberalt.singularity.presenter.google.series;

import java.util.*;
import java.util.function.Function;

public class SeriesDataAggregator implements SeriesProvider {

    private final List<SeriesProvider> seriesProviders = new ArrayList<>();

    public SeriesDataAggregator addSeriesProvider(SeriesProvider seriesProvider) {
        this.seriesProviders.add(seriesProvider);
        return this;
    }

    /** Every series on the same axis, so that a row means the same bar in all of them. */
    public Optional<SeriesChunk> provide(BarAxis axis, long stepInterval) {
        if (axis.isEmpty()) {
            return Optional.empty();
        }

        return merge(axis.rowsAt(stepInterval), provider -> provider.provide(axis, stepInterval));
    }

    private Optional<SeriesChunk> merge(int expectedRows, Function<SeriesProvider, Optional<SeriesChunk>> provide) {
        List<Column> columns = new ArrayList<>();
        List<Map<String, Object>> options = new ArrayList<>();
        List<SeriesChunk> seriesChunks = new ArrayList<>();

        for (SeriesProvider seriesProvider : seriesProviders) {
            Optional<SeriesChunk> seriesOptional = provide.apply(seriesProvider);

            if (seriesOptional.isEmpty()) {
                continue;
            }

            SeriesChunk seriesChunk = seriesOptional.get();

            if (seriesChunk.data().length != expectedRows) {
                throw new IllegalStateException("Mismatch between expected and actual rows in data array.");
            }

            seriesChunks.add(seriesChunk);
            columns.addAll(seriesChunk.columns());
            options.addAll(seriesChunk.options());
        }

        if (columns.isEmpty() || seriesChunks.isEmpty()) {
            return Optional.empty();
        }

        Object[][] data = new Object[expectedRows][columns.size()];
        int columnOffset = 0;

        for (SeriesChunk seriesChunk : seriesChunks) {
            Object[][] chunkData = seriesChunk.data();

            for (int row = 0; row < expectedRows; row++) {
                System.arraycopy(chunkData[row], 0, data[row], columnOffset, chunkData[row].length);
            }

            columnOffset += chunkData[0].length;
        }

        return Optional.of(new SeriesChunk(columns, data, options));
    }
}
