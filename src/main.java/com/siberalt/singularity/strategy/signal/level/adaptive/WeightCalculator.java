package com.siberalt.singularity.strategy.signal.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;

import java.util.List;

@FunctionalInterface
public interface WeightCalculator {
    WeightFactors compute(
        Signal levelsSignal,
        Signal volumeSignal,
        List<Candle> recentCandles,
        LevelPair levelPair
    );
}
