package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface LevelDetector {
    List<Level<Double>> detect(List<Candle> candles);
}
