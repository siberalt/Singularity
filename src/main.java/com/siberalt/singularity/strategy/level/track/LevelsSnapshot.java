package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.strategy.level.Level;

import java.time.Instant;
import java.util.List;

public record LevelsSnapshot(
    long fromIndex,
    long toIndex,
    Instant timeFrom,
    Instant timeTo,
    List<Level<Double>> levels
) {
}
