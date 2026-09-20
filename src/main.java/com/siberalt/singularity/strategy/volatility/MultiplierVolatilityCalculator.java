package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public record MultiplierVolatilityCalculator(VolatilityCalculator delegate, double multiplier) implements VolatilityCalculator {
    @Override
    public double calculate(List<Candle> candles) {
        return delegate.calculate(candles) * multiplier;
    }

    /** Множитель применяется у каждого бара: локальность - дело вложенного калькулятора. */
    @Override
    public double[] profile(List<Candle> candles) {
        double[] profile = delegate.profile(candles);

        for (int at = 0; at < profile.length; at++) {
            profile[at] *= multiplier;
        }

        return profile;
    }
}
