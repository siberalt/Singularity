package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;

public class BasicLevelBasedUpsideCalculator implements LevelBasedUpsideCalculator {
    @Override
    public Upside calculate(double currentPrice, Level<Double> resistance, Level<Double> support) {
        double resistancePrice = resistance.getFunction().apply((double) resistance.getIndexTo());
        double supportPrice = support.getFunction().apply((double) support.getIndexTo());
        double resistanceStrength = resistance.getStrength();
        double supportStrength = support.getStrength();

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
            upside = -1 - breakoutStrength;
        } else if (currentPrice > resistancePrice) {
            // Пробой сопротивления вверх - бычий сигнал
            double distanceAboveResistance = currentPrice - resistancePrice;
            double breakoutStrength = distanceAboveResistance / channelWidth * resistanceStrength;
            upside = 1 + breakoutStrength;
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
