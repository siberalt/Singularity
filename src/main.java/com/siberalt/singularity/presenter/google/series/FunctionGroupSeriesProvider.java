package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.presenter.google.PriceChart;

import java.util.*;
import java.util.function.Function;

public class FunctionGroupSeriesProvider implements SeriesProvider {
    public static class FunctionDetails {
        private final long x1;
        private final long x2;
        private final int order; // Order of addition, used for tie-breaking in redundancy filtering
        private final Function<Double, Double> function;
        private final Map<Long, Annotation> annotations;
        private List<Column> columns = new ArrayList<>();

        private FunctionDetails(long x1, long x2, int order, Function<Double, Double> function, Map<Long, Annotation> annotations) {
            this.x1 = x1;
            this.x2 = x2;
            this.order = order;
            this.function = function;
            this.annotations = annotations;
        }

        private void setColumns(List<Column> columns) {
            this.columns = columns;
        }

        public int getOrder() {
            return order;
        }

        public boolean isSubsetOf(FunctionDetails other) {
            return this.x1 >= other.x1 && this.x2 <= other.x2 && this.function.equals(other.function);
        }

        public boolean isNeighborOf(FunctionDetails other) {
            return this.function.equals(other.function) && (
                (this.x1 <= other.x2 && this.x1 >= other.x1) || (this.x2 >= other.x1 && this.x2 <= other.x2)
            );
        }
    }

    public static class FunctionDetailsBuilder {
        private static int idCounter = 0;
        private final long x1;
        private final long x2;
        private int order = -1;
        private final Function<Double, Double> function;
        private final Map<Long, Annotation> annotations = new HashMap<>();

        public FunctionDetailsBuilder(long x1, long x2, Function<Double, Double> function) {
            this.x1 = x1;
            this.x2 = x2;
            this.function = function;
        }

        public FunctionDetailsBuilder setOrder(int order) {
            this.order = order;
            return this;
        }

        public FunctionDetailsBuilder addAnnotation(long x, Annotation annotation) {
            this.annotations.put(x, annotation);
            return this;
        }

        public FunctionDetails build() {
            if (order == -1) {
                this.order = ++idCounter; // Assign a unique order if not set
            }

            return new FunctionDetails(x1, x2, order, function, annotations);
        }
    }

    private String color = "#00FF00"; // Default color
    private final String title;
    private int lineWidth = 2; // Default line width
    private final Map<Function<Double, Double>, List<FunctionDetails>> functionDetails = new HashMap<>();

    public FunctionGroupSeriesProvider(String title) {
        this.title = title;
    }

    public void addFunction(FunctionDetails functionDetails) {
        if (this.functionDetails.containsKey(functionDetails.function)) {
            if (filterRedundantFunctions(functionDetails)) {
                return; // New function is redundant, do not add
            }
        }

        List<Column> columns = new ArrayList<>(List.of(new Column(ColumnType.NUMBER, ColumnRole.DATA, title)));

        if (!functionDetails.annotations.isEmpty()) {
            columns.add(new Column(ColumnType.STRING, ColumnRole.ANNOTATION));
            columns.add(new Column(ColumnType.STRING, ColumnRole.ANNOTATION_TEXT));
        }

        functionDetails.setColumns(columns);

        this.functionDetails.computeIfAbsent(
            functionDetails.function, k -> new ArrayList<>()
        ).add(functionDetails);
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

        if (functionDetails.isEmpty()) {
            return Optional.empty(); // No lines to provide
        }

        List<Map<String, Object>> optionsList = new ArrayList<>();

        List<FunctionDetails> functions = this.functionDetails.values().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparingInt(FunctionDetails::getOrder)) // Sort by order of addition
            .toList();

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

    /**
     * Filters out redundant functions from the list of `FunctionDetails` associated with the same function.
     * <p>
     * A function is considered redundant if:
     * - It is a subset of an existing function (i.e., its range is fully contained within the range of another function).
     * <p>
     * If a function is not redundant but overlaps or is adjacent to an existing function:
     * - The overlapping or neighboring function is removed to avoid duplication.
     *
     * @param functionDetails The `FunctionDetails` object to be checked for redundancy.
     * @return `true` if the provided `FunctionDetails` is redundant and should not be added, `false` otherwise.
     */
    private boolean filterRedundantFunctions(FunctionDetails functionDetails) {
        Iterator<FunctionDetails> iterator = this.functionDetails.get(functionDetails.function).iterator();

        while (iterator.hasNext()) {
            FunctionDetails existingFunction = iterator.next();

            if (functionDetails.isSubsetOf(existingFunction)) {
                return true; // New function is redundant, do not add
            } else if (
                existingFunction.isSubsetOf(functionDetails)
                    || existingFunction.isNeighborOf(functionDetails)
            ) {
                iterator.remove();
            }
        }

        return false;
    }
}
