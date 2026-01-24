package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface ExtremeLocator {
    List<Candle> locate(List<Candle> candles);
}
