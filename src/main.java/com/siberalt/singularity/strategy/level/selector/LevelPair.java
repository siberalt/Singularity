package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.strategy.level.Level;

public record LevelPair(Level<Double> resistance, Level<Double> support) {
    public static final LevelPair EMPTY = new LevelPair(null,null);
}
