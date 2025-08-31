package com.siberalt.singularity.presenter.google.series;

import java.util.*;

public class SeriesDataAggregator implements SeriesProvider {

    private final List<SeriesProvider> seriesProviders = new ArrayList<>();

    public SeriesDataAggregator addSeriesProvider(SeriesProvider seriesProvider) {
        this.seriesProviders.add(seriesProvider);
        return this;
    }

    public Optional<SeriesChunk> provide(long start, long end, long stepInterval) {
        // Calculate the expected number of rows
        int expectedRows = (int) ((end - start) / stepInterval + 1);
        List<Column> columns = new ArrayList<>();
        List<Map<String, Object>> options = new ArrayList<>();
        List<SeriesChunk> seriesChunks = new ArrayList<>();

        for (SeriesProvider seriesProvider : seriesProviders) {
            Optional<SeriesChunk> seriesOptional = seriesProvider.provide(start, end, stepInterval);

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
