package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface VolatilityCalculator {
    double calculate(List<Candle> candles);
}
