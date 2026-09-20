package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Arrays;
import java.util.List;

public interface VolatilityCalculator {
    double calculate(List<Candle> candles);

    /**
     * Волатильность у каждого бара окна, по позиции бара в списке.
     * <p>
     * По умолчанию - одна и та же на всё окно, как было до появления этого метода. Переопределять его
     * стоит тем, кто умеет мерить локально: окно поиска уровней - это полгода, а одно число на полгода
     * судит минимумы полугодовой давности линейкой сегодняшнего дня.
     */
    default double[] profile(List<Candle> candles) {
        double[] profile = new double[candles == null ? 0 : candles.size()];

        Arrays.fill(profile, calculate(candles));

        return profile;
    }
}
