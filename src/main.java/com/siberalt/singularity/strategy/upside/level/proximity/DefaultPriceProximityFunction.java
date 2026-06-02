package com.siberalt.singularity.strategy.upside.level.proximity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.List;
import java.util.function.Function;

public record DefaultPriceProximityFunction(
    Function<Candle, Double> priceExtractor,
    double decayFactor,
    double multiplier,
    VolatilityCalculator volatilityCalculator
) implements PriceProximityFunction {

    public DefaultPriceProximityFunction() {
        this(Candle::getCloseAsDouble, 10.0, 0.5, new ATRVolatilityCalculator(14));
    }

    @Override
    public double compute(Candle candle, Level<Double> level, List<Candle> lastCandles) {
        double levelPrice = level.function().apply((double) candle.getIndex());
        double currentPrice = priceExtractor.apply(candle);
        double absoluteDistance = Math.abs(levelPrice - currentPrice);

        // Волатильность за тот же период (можно и за последние N, но проще за все)
        double volatility = volatilityCalculator.calculate(lastCandles);
        if (volatility <= 1e-6) {
            // Если волатильность почти нулевая, используем абсолютное расстояние с большим штрафом
            return Math.exp(-decayFactor * absoluteDistance / (currentPrice * 0.001));
        }

        // Нормированное расстояние в единицах волатильности
        double normalizedDistance = absoluteDistance / (volatility * multiplier);
        // Экспоненциальное затухание: при normalizedDistance = 0 -> 1, при =1 -> exp(-decayFactor)
        return Math.exp(-decayFactor * normalizedDistance);
    }
}
