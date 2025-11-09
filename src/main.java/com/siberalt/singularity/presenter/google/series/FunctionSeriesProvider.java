package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.presenter.google.PriceChart;

import java.util.*;
import java.util.function.Function;

public class FunctionSeriesProvider implements SeriesProvider {
    record Segment(long x1, long x2) implements Comparable<Segment> {
        @Override
        public int compareTo(Segment o) {
            if (o.isOverlapping(this) || this.isOverlapping(o)) {
                return 0; // Overlapping segments are considered equal
            }

            return Long.compare(this.x1, o.x1);
        }

        public boolean isOverlapping(Segment other) {
            return this.x1 < other.x2 && this.x2 > other.x1;
        }
    }

    private String color = "#00FF00"; // Default color
    private final String title;
    private int lineWidth = 2; // Default line width
    private final HashMap<Long, Annotation> annotations = new HashMap<>();
    private final TreeMap<Segment, Function<Double, Double>> lines = new TreeMap<>();

    public FunctionSeriesProvider(String title) {
        this.title = title;
    }

    public FunctionSeriesProvider setColor(String color) {
        this.color = color;
        return this;
    }

    public FunctionSeriesProvider setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        return this;
    }

    public void addFunction(long x1, long x2, Function<Double, Double> function) {
        if (x1 >= x2) {
            throw new IllegalArgumentException("x1 must be less than x2");
        }

        Segment segment = new Segment(x1, x2);

        if (lines.containsKey(segment)) {
            throw new IllegalArgumentException("Line segment already exists or overlaps with another segment");
        }

        lines.put(segment, function);
    }

    public void addAnnotation(long x, Annotation annotation) {
        if (annotations.containsKey(x)) {
            throw new IllegalArgumentException("Annotation already exists at this x-coordinate");
        }
        annotations.put(x, annotation);
    }

    @Override
    public Optional<SeriesChunk> provide(long start, long end, long stepInterval) {
        if (start > end) {
            throw new IllegalArgumentException("Start must be less than end");
        }

        if (stepInterval <= 0) {
            throw new IllegalArgumentException("Step interval must be greater than zero");
        }

        if (lines.isEmpty()) {
            return Optional.empty(); // No lines to provide
        }

        List<Column> columns = new ArrayList<>(List.of(new Column(ColumnType.NUMBER, ColumnRole.DATA, title)));

        if (!annotations.isEmpty()) {
            columns.add(new Column(ColumnType.STRING, ColumnRole.ANNOTATION));
            columns.add(new Column(ColumnType.STRING, ColumnRole.ANNOTATION_TEXT));
        }

        Object[][] data = new Object[(int) ((end - start) / stepInterval + 1)][columns.size()];

        for (Segment segment : lines.keySet()) {
            if (segment.x2 < start || segment.x1 > end) {
                continue; // Skip segments outside the requested range
            }

            long effectiveStart = Math.max(PriceChart.adjustToStepInterval(segment.x1, stepInterval), start);
            long effectiveEnd = Math.min(PriceChart.adjustToStepInterval(segment.x2, stepInterval), end);
            Function<Double, Double> function = lines.get(segment);

            for (long x = effectiveStart; x <= effectiveEnd; x += stepInterval) {
                int rowIndex = (int) ((x - start) / stepInterval);

                data[rowIndex][0] = function.apply((double) x);

                if (!annotations.isEmpty()) {
                    var annotation = annotations.getOrDefault(x, null);

                    if (annotation == null) {
                        data[rowIndex][1] = null; // Annotation label
                        data[rowIndex][2] = null; // Annotation text
                    } else {
                        data[rowIndex][1] = annotation.label();
                        data[rowIndex][2] = annotation.text();
                    }
                }
            }
        }

        Map<String, Object> options = new HashMap<>();
        options.put("color", color);
        options.put("lineWidth", lineWidth);

        return Optional.of(new SeriesChunk(columns, data, List.of(options)));
    }
}
