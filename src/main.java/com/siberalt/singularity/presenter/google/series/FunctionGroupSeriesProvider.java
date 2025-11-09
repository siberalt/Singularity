package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.presenter.google.PriceChart;

import java.util.*;
import java.util.function.Function;

public class FunctionGroupSeriesProvider implements SeriesProvider {
    public static class FunctionDetails {
        private final long x1;
        private final long x2;
        private final Function<Double, Double> function;
        private final Map<Long, Annotation> annotations;
        private List<Column> columns = new ArrayList<>();

        private FunctionDetails(long x1, long x2, Function<Double, Double> function, Map<Long, Annotation> annotations) {
            this.x1 = x1;
            this.x2 = x2;
            this.function = function;
            this.annotations = annotations;
        }

        private void setColumns(List<Column> columns) {
            this.columns = columns;
        }

        public boolean isSubsetOf(FunctionDetails other) {
            return this.x1 >= other.x1 && this.x2 <= other.x2 && this.function.equals(other.function);
        }
    }

    public static class FunctionDetailsBuilder {
        private final long x1;
        private final long x2;
        private final Function<Double, Double> function;
        private final Map<Long, Annotation> annotations = new HashMap<>();

        public FunctionDetailsBuilder(long x1, long x2, Function<Double, Double> function) {
            this.x1 = x1;
            this.x2 = x2;
            this.function = function;
        }

        public FunctionDetailsBuilder addAnnotation(long x, Annotation annotation) {
            this.annotations.put(x, annotation);
            return this;
        }

        public FunctionDetails build() {
            return new FunctionDetails(x1, x2, function, annotations);
        }
    }

    private String color = "#00FF00"; // Default color
    private final String title;
    private int lineWidth = 2; // Default line width
    private final List<FunctionDetails> functions = new ArrayList<>();

    public FunctionGroupSeriesProvider(String title) {
        this.title = title;
    }

    public void addFunction(FunctionDetails functionDetails) {
        Iterator<FunctionDetails> iterator = functions.iterator();

        while (iterator.hasNext()) {
            FunctionDetails existingFunction = iterator.next();

            if (functionDetails.isSubsetOf(existingFunction)) {
                return;
            } else if (existingFunction.isSubsetOf(functionDetails)) {
                iterator.remove();
            }
        }

        List<Column> columns = new ArrayList<>(List.of(new Column(ColumnType.NUMBER, ColumnRole.DATA, title)));

        if (!functionDetails.annotations.isEmpty()) {
            columns.add(new Column(ColumnType.STRING, ColumnRole.ANNOTATION));
            columns.add(new Column(ColumnType.STRING, ColumnRole.ANNOTATION_TEXT));
        }

        functionDetails.setColumns(columns);
        functions.add(functionDetails);
    }

    public void addFunction(long x1, long x2, Function<Double, Double> function) {
        addFunction(new FunctionDetailsBuilder(x1, x2, function).build());
    }

    public FunctionGroupSeriesProvider setColor(String color) {
        this.color = color;
        return this;
    }

    public FunctionGroupSeriesProvider setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        return this;
    }

    @Override
    public Optional<SeriesChunk> provide(long start, long end, long stepInterval) {
        if (start > end) {
            throw new IllegalArgumentException("Start must be less than end");
        }

        if (stepInterval <= 0) {
            throw new IllegalArgumentException("Step interval must be greater than zero");
        }

        if (functions.isEmpty()) {
            return Optional.empty(); // No lines to provide
        }

        List<Map<String, Object>> optionsList = new ArrayList<>();

        List<Column> allColumns = functions.stream()
            .flatMap(fd -> fd.columns.stream())
            .toList();

        for (Column column : allColumns) {
            if (column.role().equals(ColumnRole.DATA)) {
                Map<String, Object> options = new HashMap<>();
                options.put("color", color);
                options.put("lineWidth", lineWidth);
                optionsList.add(options);
            } else {
                optionsList.add(Collections.emptyMap());
            }
        }

        Object[][] data = new Object[(int) ((end - start) / stepInterval + 1)][allColumns.size()];
        int columnOffset = 0;

        for (FunctionDetails functionDetails : functions) {
            if (functionDetails.x2 < start || functionDetails.x1 > end) {
                continue; // Skip segments outside the requested range
            }

            long effectiveStart = Math.max(PriceChart.adjustToStepInterval(functionDetails.x1, stepInterval), start);
            long effectiveEnd = Math.min(PriceChart.adjustToStepInterval(functionDetails.x2, stepInterval), end);
            Function<Double, Double> function = functionDetails.function;

            for (long x = effectiveStart; x <= effectiveEnd; x += stepInterval) {
                int rowIndex = (int) ((x - start) / stepInterval);

                data[rowIndex][columnOffset] = function.apply((double) x);

                if (functionDetails.annotations.containsKey(x)) {
                    var annotation = functionDetails.annotations.get(x);

                    data[rowIndex][columnOffset + 1] = annotation.label();
                    data[rowIndex][columnOffset + 2] = annotation.text();
                } else if (!functionDetails.annotations.isEmpty()) {
                    data[rowIndex][columnOffset + 1] = null; // Annotation label
                    data[rowIndex][columnOffset + 2] = null; // Annotation text
                }
            }

            columnOffset += functionDetails.columns.size();
        }

        return Optional.of(new SeriesChunk(allColumns, data, optionsList));
    }

    public static FunctionDetailsBuilder newFunctionBuilder(long x1, long x2, Function<Double, Double> function) {
        return new FunctionDetailsBuilder(x1, x2, function);
    }

    public static FunctionDetails createFunctionDetails(long x1, long x2, Function<Double, Double> function) {
        return new FunctionDetailsBuilder(x1, x2, function).build();
    }
}
