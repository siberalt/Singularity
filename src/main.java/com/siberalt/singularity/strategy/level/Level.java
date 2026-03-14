package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.TimePoint;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

public record Level<T extends Number>(
    String id,
    TimePoint pointFrom,
    TimePoint pointTo,
    Function<T, T> function,
    double strength) {

    public Level(TimePoint pointFrom, TimePoint pointTo, Function<T, T> function, double strength) {
        this(UUID.randomUUID().toString(), pointFrom, pointTo, function, strength);
    }

    public Level(long indexFrom, long indexTo, Function<T, T> function) {
        this(new TimePoint(indexFrom), new TimePoint(indexTo), function, 0);
    }

    public long indexFrom() {
        return pointFrom.index();
    }

    public long indexTo() {
        return pointTo.index();
    }

    public Instant timeFrom() {
        return pointFrom.time();
    }

    public Instant timeTo() {
        return pointTo.time();
    }

    public boolean containsIndex(long index) {
        return pointFrom.index() <= index && index <= pointTo.index();
    }

    public boolean intersects(Level<T> other) {
        return pointFrom.index() <= other.pointTo().index() && other.pointFrom().index() <= pointTo.index();
    }
}
