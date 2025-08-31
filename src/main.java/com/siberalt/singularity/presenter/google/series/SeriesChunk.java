package com.siberalt.singularity.presenter.google.series;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a chunk of series data, including columns, data, and options.
 * This class is implemented as a Java record, which is a concise way to define
 * immutable data objects.
 *
 * @param columns A list of columns representing the structure of the data.
 * @param data A 2D array containing the actual data values.
 * @param options A list of maps containing additional options or metadata for each column.
 */
public record SeriesChunk(List<Column> columns, Object[][] data, List<Map<String, Object>> options) {

    /**
     * Constructs a new SeriesChunk with the specified columns and data.
     * Initializes the options as an empty immutable list.
     *
     * @param columns A list of columns representing the structure of the data.
     * @param data A 2D array containing the actual data values.
     */
    public SeriesChunk(List<Column> columns, Object[][] data) {
        this(columns, data, Collections.emptyList());
    }
}
