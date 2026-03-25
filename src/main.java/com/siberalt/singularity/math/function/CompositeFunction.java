package com.siberalt.singularity.math.function;

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Function;

public class CompositeFunction<T, R> implements Function<T, R> {
    private record RangeFunction<T, R>(T fromX, T toX, Function<T, R> function) {
    }

    private final TreeMap<T, RangeFunction<T, R>> functions;
    private final Comparator<T> comparator;
    private final R defaultValue;

    public CompositeFunction(Comparator<T> comparator) {
        this.comparator = comparator;
        this.functions = new TreeMap<>(comparator);
        this.defaultValue = null;
    }

    public CompositeFunction(Comparator<T> comparator, R defaultValue) {
        this.comparator = comparator;
        this.functions = new TreeMap<>(comparator);
        this.defaultValue = defaultValue;
    }

    public void addFunction(T fromX, T toX, Function<T, R> function) {
        functions.put(fromX, new RangeFunction<>(fromX, toX, function));
    }

    public T getFromX() {
        return functions.firstEntry().getValue().fromX;
    }

    public T getToX() {
        return functions.lastEntry().getValue().toX;
    }

    public static CompositeFunction<Double, Double> createForDouble() {
        return new CompositeFunction<>(Comparator.comparingDouble(o -> o));
    }

    @Override
    public R apply(T t) {
        NavigableMap.Entry<T, RangeFunction<T, R>> entry = functions.floorEntry(t);
        if (entry == null || comparator.compare(t, entry.getValue().toX) > 0) {
            return defaultValue;
        }
        return entry.getValue().function().apply(t);
    }
}
