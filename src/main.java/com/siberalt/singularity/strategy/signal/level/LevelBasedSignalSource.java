package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;

import java.util.List;

public interface LevelBasedSignalSource {
    Signal calculate(LevelPair level, List<Candle> recentCandles);
}
