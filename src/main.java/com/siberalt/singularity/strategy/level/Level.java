package com.siberalt.singularity.strategy.level;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

public record Level<T extends Number>(
    String id,
    Instant timeFrom,
    Instant timeTo,
    long indexFrom,
    long indexTo,
    Function<T, T> function,
    double strength) {

    public Level(Instant timeFrom, Instant timeTo, long indexFrom, long indexTo, Function<T, T> function, double strength) {
        this(UUID.randomUUID().toString(), timeFrom, timeTo, indexFrom, indexTo, function, strength);
    }

    public Level(long indexFrom, long indexTo, Function<T, T> function) {
        this(null, null, indexFrom, indexTo, function, 0);
    }

    public boolean containsIndex(long index) {
        return index >= indexFrom && index <= indexTo;
    }

    public boolean intersects(Level<T> other) {
        return this.indexFrom <= other.indexTo && other.indexFrom <= this.indexTo;
    }
}
