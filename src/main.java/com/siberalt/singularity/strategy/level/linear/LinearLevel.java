package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.math.LinearFunction2D;
import com.siberalt.singularity.strategy.level.Level;

import java.time.Instant;
import java.util.function.Function;

public record LinearLevel<T extends Number>(
    Instant from,
    Instant to,
    long indexFrom,
    long indexTo,
    LinearFunction2D<T> function,
    double strength
) implements Level<T> {
    @Override
    public Instant getTimeFrom() {
        return from;
    }

    @Override
    public Instant getTimeTo() {
        return to;
    }

    @Override
    public long getIndexFrom() {
        return indexFrom;
    }

    @Override
    public long getIndexTo() {
        return indexTo;
    }

    @Override
    public Function<T, T> getFunction() {
        return function;
    }

    @Override
    public double getStrength() {
        return strength;
    }
}
