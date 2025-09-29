package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;

public interface LevelBasedUpsideCalculator {
    Upside calculate(double currentPrice, Level<Double> resistance, Level<Double> support);
}
