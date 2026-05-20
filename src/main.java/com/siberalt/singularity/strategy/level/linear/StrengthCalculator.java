package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;

import java.util.List;

public interface StrengthCalculator {
    double calculate(Level<Double> level, List<Candle> candles);
}
