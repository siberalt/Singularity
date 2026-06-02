package com.siberalt.singularity.strategy.upside.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;

@FunctionalInterface
public interface WeightCalculator {
    WeightFactors compute(
        Upside levelsUpside,
        Upside volumeUpside,
        List<Candle> recentCandles,
        LevelPair levelPair
    );
}
