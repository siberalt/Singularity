package com.siberalt.singularity.presenter.google.series;


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

    /**
     * Each function drawn over the rows of the bars its range of candle indices covers, and read at
     * the index of each bar's first candle - kept inside the function's own range, so a line that
     * starts in the middle of a bar starts at its own first value. The functions keep speaking in
     * the indices they were fitted on, which is what lets a level found on minute candles lie over
     * an hourly chart.
     */
    @Override
    public Optional<SeriesChunk> provide(BarAxis axis, long stepInterval) {
        if (stepInterval <= 0) {
            throw new IllegalArgumentException("Step interval must be greater than zero");
        }

        if (functionDetails.isEmpty() || axis.isEmpty()) {
            return Optional.empty();
        }

        List<FunctionDetails> functions = this.functionDetails.values().stream()
            .flatMap(List::stream)
            .sorted(Comparator.comparingInt(FunctionDetails::getOrder))
            .toList();

        List<Column> allColumns = functions.stream()
            .flatMap(fd -> fd.columns.stream())
            .toList();

        List<Map<String, Object>> optionsList = new ArrayList<>();

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

        Object[][] data = new Object[axis.rowsAt(stepInterval)][allColumns.size()];
        int columnOffset = 0;

        for (FunctionDetails details : functions) {
            int firstRow = axis.rowOfIndex(details.x1);
            int lastRow = axis.rowOfIndex(details.x2);

            if (lastRow >= 0 && firstRow < axis.size()) {
                Map<Integer, Annotation> annotationsByRow = new HashMap<>();
                details.annotations.forEach((x, annotation) -> annotationsByRow.put(axis.rowOfIndex(x), annotation));

                firstRow = Math.max(0, firstRow);
                lastRow = Math.min(axis.size() - 1, lastRow);
                int row = (int) (Math.ceilDiv(firstRow, stepInterval) * stepInterval);

                for (; row <= lastRow; row += (int) stepInterval) {
                    int dataRow = (int) (row / stepInterval);
                    long x = Math.clamp(axis.indexAt(row), details.x1, details.x2);

                    data[dataRow][columnOffset] = details.function.apply((double) x);

                    if (!details.annotations.isEmpty()) {
                        Annotation annotation = annotationsByRow.get(row);
                        data[dataRow][columnOffset + 1] = annotation == null ? null : annotation.label();
                        data[dataRow][columnOffset + 2] = annotation == null ? null : annotation.text();
                    }
                }
            }

            columnOffset += details.columns.size();
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
