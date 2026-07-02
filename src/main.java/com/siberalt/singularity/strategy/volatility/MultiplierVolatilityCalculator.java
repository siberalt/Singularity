package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public record MultiplierVolatilityCalculator(VolatilityCalculator delegate, double multiplier) implements VolatilityCalculator {
    @Override
    public double calculate(List<Candle> candles) {
        return delegate.calculate(candles) * multiplier;
    }
}
