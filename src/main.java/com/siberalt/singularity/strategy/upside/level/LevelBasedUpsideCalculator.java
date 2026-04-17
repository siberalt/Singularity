package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;

public interface LevelBasedUpsideCalculator {
    Upside calculate(LevelPair level, List<Candle> recentCandles);
}
