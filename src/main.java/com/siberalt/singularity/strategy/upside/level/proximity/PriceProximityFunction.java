package com.siberalt.singularity.strategy.upside.level.proximity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;

import java.util.List;

@FunctionalInterface
public interface PriceProximityFunction {
    /**
     * Вычисляет меру близости.
     *
     * @param candle текущая свеча
     * @param level уровень
     * @return значение близости (0 = далеко, 1 = очень близко)
     */
    double compute(Candle candle, Level<Double> level, List<Candle> lastCandles);
}
