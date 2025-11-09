package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.util.List;

public interface LevelDetector<T extends Number> {
    List<Level<Double>> detect(List<Candle> candles, CandleIndexProvider candleIndexProvider);
}
