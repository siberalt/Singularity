package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface LevelDetector<T extends Number> {
    List<? extends Level<T>> detect(List<Candle> candles);
}
