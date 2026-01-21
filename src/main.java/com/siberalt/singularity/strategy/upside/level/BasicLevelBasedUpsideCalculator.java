package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;

public class BasicLevelBasedUpsideCalculator implements LevelBasedUpsideCalculator {
    private final double bullishBreakoutSensitivity;
    private final double bearishBreakoutSensitivity;

    public BasicLevelBasedUpsideCalculator() {
        this.bullishBreakoutSensitivity = 5;
        this.bearishBreakoutSensitivity = -5;
    }

    public BasicLevelBasedUpsideCalculator(double bullishBreakoutSensitivity, double bearishBreakoutSensitivity) {
        this.bullishBreakoutSensitivity = bullishBreakoutSensitivity;
        this.bearishBreakoutSensitivity = bearishBreakoutSensitivity;
    }

    @Override
    public Upside calculate(
        Level<Double> resistance,
        Level<Double> support,
        List<Candle> recentCandles
    ) {
        long currentIndex = recentCandles.get(recentCandles.size() - 1).getIndex();
        double resistancePrice = resistance.function().apply((double) currentIndex);
        double supportPrice = support.function().apply((double) currentIndex);
        double resistanceStrength = resistance.strength();
        double supportStrength = support.strength();
        double currentPrice = recentCandles.get(recentCandles.size() - 1).getTypicalPrice().toDouble();

        if (resistancePrice <= supportPrice) {
            // Log a warning and return a neutral Upside
            System.err.println("Warning: Resistance price must be greater than support price. Returning neutral Upside.");
            return new Upside(0, 0);
        }

        double channelWidth = resistancePrice - supportPrice;
        double weightedNeutralPoint = (supportStrength * resistancePrice + resistanceStrength * supportPrice)
            / (supportStrength + resistanceStrength);

        double upside;

        // Определяем положение цены относительно уровней
        if (currentPrice < supportPrice) {
            // Пробой поддержки вниз - медвежий сигнал
            double distanceBelowSupport = supportPrice - currentPrice;
            double breakoutStrength = distanceBelowSupport / channelWidth * supportStrength;
            upside = bearishBreakoutSensitivity * breakoutStrength;
        } else if (currentPrice > resistancePrice) {
            // Пробой сопротивления вверх - бычий сигнал
            double distanceAboveResistance = currentPrice - resistancePrice;
            double breakoutStrength = distanceAboveResistance / channelWidth * resistanceStrength;
            upside = bullishBreakoutSensitivity * breakoutStrength;
        } else {
            // Внутри канала - используем линейную интерполяцию
            if (currentPrice <= weightedNeutralPoint) {
                // Между поддержкой и нейтральной точкой
                upside = 1 - (currentPrice - supportPrice) / (weightedNeutralPoint - supportPrice);
            } else {
                // Между нейтральной точкой и сопротивлением
                upside = - (currentPrice - weightedNeutralPoint) / (resistancePrice - weightedNeutralPoint);
            }
        }

        // Нормализуем сигнал и вычисляем силу
        double normalizedSignal = Math.max(-1, Math.min(1, upside));

        return new Upside(normalizedSignal, upside);
    }
}
