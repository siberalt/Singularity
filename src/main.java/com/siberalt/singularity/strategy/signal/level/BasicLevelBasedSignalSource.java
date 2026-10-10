package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;

import java.util.List;

public class BasicLevelBasedSignalSource implements LevelBasedSignalSource {
    private final double bullishBreakoutSensitivity;
    private final double bearishBreakoutSensitivity;

    public BasicLevelBasedSignalSource() {
        this.bullishBreakoutSensitivity = 5;
        this.bearishBreakoutSensitivity = -5;
    }

    public BasicLevelBasedSignalSource(double bullishBreakoutSensitivity, double bearishBreakoutSensitivity) {
        this.bullishBreakoutSensitivity = bullishBreakoutSensitivity;
        this.bearishBreakoutSensitivity = bearishBreakoutSensitivity;
    }

    @Override
    public Signal calculate(LevelPair levelPair, List<Candle> recentCandles) {
        var resistance = levelPair.resistance();
        var support = levelPair.support();

        long currentIndex = recentCandles.get(recentCandles.size() - 1).getIndex();
        double resistancePrice = resistance.function().apply((double) currentIndex);
        double supportPrice = support.function().apply((double) currentIndex);
        double resistanceStrength = resistance.strength();
        double supportStrength = support.strength();
        double currentPrice = recentCandles.get(recentCandles.size() - 1).getTypicalAsDouble();

        if (resistancePrice <= supportPrice) {
            // Log a warning and return a neutral Signal
            System.err.println("Warning: Resistance price must be greater than support price. Returning neutral Signal.");
            return Signal.NEUTRAL;
        }

        double channelWidth = resistancePrice - supportPrice;
        double weightedNeutralPoint = (supportStrength * resistancePrice + resistanceStrength * supportPrice)
            / (supportStrength + resistanceStrength);

        double signal;

        // Определяем положение цены относительно уровней
        if (currentPrice < supportPrice) {
            // Пробой поддержки вниз - медвежий сигнал
            double distanceBelowSupport = supportPrice - currentPrice;
            double breakoutStrength = distanceBelowSupport / channelWidth * supportStrength;
            signal = bearishBreakoutSensitivity * breakoutStrength;
        } else if (currentPrice > resistancePrice) {
            // Пробой сопротивления вверх - бычий сигнал
            double distanceAboveResistance = currentPrice - resistancePrice;
            double breakoutStrength = distanceAboveResistance / channelWidth * resistanceStrength;
            signal = bullishBreakoutSensitivity * breakoutStrength;
        } else {
            // Внутри канала - используем линейную интерполяцию
            if (currentPrice <= weightedNeutralPoint) {
                // Между поддержкой и нейтральной точкой
                signal = 1 - (currentPrice - supportPrice) / (weightedNeutralPoint - supportPrice);
            } else {
                // Между нейтральной точкой и сопротивлением
                signal = -(currentPrice - weightedNeutralPoint) / (resistancePrice - weightedNeutralPoint);
            }
        }

        // Нормализуем сигнал и вычисляем силу
        double normalizedSignal = Math.max(-1, Math.min(1, signal));

        return new Signal(normalizedSignal, signal);
    }
}
