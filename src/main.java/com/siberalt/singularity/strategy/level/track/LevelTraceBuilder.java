package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.math.function.CompositeFunction;
import com.siberalt.singularity.shared.TimePointRange;

import java.util.UUID;
import java.util.function.Function;

public class LevelTraceBuilder {
    private final CompositeFunction<Double, Double> compositeFunction = CompositeFunction.createForDouble();
    private TimePointRange timePointRange;

    public LevelTraceBuilder addFunction(TimePoint from, TimePoint to, Function<Double, Double> function) {
        TimePointRange newRange = new TimePointRange(from, to);

        timePointRange = timePointRange == null ? newRange : TimePointRange.union(timePointRange, newRange);
        compositeFunction.addFunction((double)newRange.fromIndex(), (double)newRange.toIndex(), function);

        return this;
    }

    public LevelTrace build() {
        if (timePointRange == null) {
            throw new IllegalStateException("No functions added to build a LevelTrace.");
        }

        return new LevelTrace(
            UUID.randomUUID(),
            compositeFunction,
            timePointRange.fromPoint(),
            timePointRange.toPoint()
        );
    }
}
