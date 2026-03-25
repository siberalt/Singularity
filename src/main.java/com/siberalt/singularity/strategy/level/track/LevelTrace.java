package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.TimePoint;

import java.util.UUID;
import java.util.function.Function;

public record LevelTrace(UUID traceId, Function<Double, Double> function, TimePoint fromPoint, TimePoint toPoint) {
}
