package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.TimePoint;

import java.util.List;

public record LevelTraceGroup(TimePoint fromPoint, TimePoint toPoint, List<LevelTrace> levelTraces) {
}
