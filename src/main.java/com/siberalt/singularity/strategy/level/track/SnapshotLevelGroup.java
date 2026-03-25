package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;

import java.time.Instant;
import java.util.List;

public record SnapshotLevelGroup(
    TimePoint fromPoint,
    TimePoint toPoint,
    List<Level<Double>> levels
) {
    public long fromIndex() {
        return fromPoint.index();
    }

    public long toIndex() {
        return toPoint.index();
    }

    public Instant fromTime() {
        return fromPoint.time();
    }

    public Instant toTime() {
        return toPoint.time();
    }
}
