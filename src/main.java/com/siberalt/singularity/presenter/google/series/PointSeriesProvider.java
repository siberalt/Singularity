package com.siberalt.singularity.presenter.google.series;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PointSeriesProvider implements SeriesProvider {
    private final String title;
    private final Map<Long, Double> points = new HashMap<>();
    private String color = "#4285F4"; // Default color
    private int size = 8; // Default point size
    private Shape shape = Shape.CIRCLE; // Default point shape

    public PointSeriesProvider(String title) {
        this.title = title;
    }

    public void addPoint(long x, double value) {
        points.put(x, value);
    }

    public PointSeriesProvider setColor(String color) {
        this.color = color;
        return this;
    }

    public PointSeriesProvider setSize(int size) {
        this.size = size;
        return this;
    }

    public PointSeriesProvider setShape(Shape shape) {
        this.shape = shape;
        return this;
    }

    /**
     * Points are placed by the candle index they were given at, in the row of the bar that covers
     * that index. An extreme found on minute candles then sits on the hour it happened in.
     */
    @Override
    public Optional<SeriesChunk> provide(BarAxis axis, long stepInterval) {
        if (points.isEmpty() || axis.isEmpty()) {
            return Optional.empty();
        }

        int rows = axis.rowsAt(stepInterval);
        Object[][] data = new Object[rows][1];
        boolean any = false;

        for (Map.Entry<Long, Double> point : points.entrySet()) {
            int row = axis.rowOfIndex(point.getKey());

            if (row < 0 || row >= axis.size()) {
                continue;
            }

            data[SeriesProvider.dataRowOf(row, stepInterval, rows)][0] = point.getValue();
            any = true;
        }

        return any ? Optional.of(new SeriesChunk(List.of(column()), data, List.of(options()))) : Optional.empty();
    }

    private Column column() {
        return new Column(ColumnType.NUMBER, ColumnRole.DATA, title);
    }

    private Map<String, Object> options() {
        return Map.of(
            "color", color,
            "pointSize", size,
            "pointShape", shape.getName()
        );
    }

}
