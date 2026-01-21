package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface ExtremumLocator {
    List<Candle> locate(List<Candle> candles);
}
