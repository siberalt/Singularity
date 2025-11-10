package com.siberalt.singularity.strategy.level.selector;

import java.time.Instant;
import java.util.List;

public record LevelPairsSnapshot(
    long fromIndex,
    long toIndex,
    Instant timeFrom,
    Instant timeTo,
    List<LevelPair> levelPairs
){}