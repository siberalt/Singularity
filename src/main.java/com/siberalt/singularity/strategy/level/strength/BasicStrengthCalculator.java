package com.siberalt.singularity.strategy.level.strength;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearFunction;
import com.siberalt.singularity.strategy.level.Level;

import java.util.List;

public class BasicStrengthCalculator implements StrengthCalculator {
    private double touchWeight = 0.4;
    private double angleWeight = 0.3;
    private double timeframeWeight = 0.3;

    public BasicStrengthCalculator() {
    }

    public BasicStrengthCalculator(double touchWeight, double angleWeight, double timeframeWeight) {
        this.touchWeight = touchWeight;
        this.angleWeight = angleWeight;
        this.timeframeWeight = timeframeWeight;
    }

    @Override
    public double calculate(Level<Double> level, List<Candle> candles) {
        if (
            level.touchesCount() <= 0
                || level.indexFrom() < 0
                || level.indexTo() < level.indexFrom()
        ) {
            return 0.0;
        }

        double angleFactor;

        if (level.function() instanceof LinearFunction<Double>) {
            double slope = Math.abs(((LinearFunction<Double>) level.function()).getSlope());
            angleFactor = calculateAngleFactor(slope);
        } else if (level.function() == null) {
            return 0.0;
        } else {
            angleFactor = 0; // Default factor if function type is unknown
        }

        double touchFactor = calculateTouchFactor(level.touchesCount());
        double timeframeFactor = calculateTimeframeFactor(level.indexTo() - level.indexFrom());

        // Пример простой формулы для расчета силы уровня
        return touchWeight * touchFactor +
            angleWeight * angleFactor +
            timeframeWeight * timeframeFactor;
    }

    private double calculateTimeframeFactor(long durationBars) {
        // Сила растет с длительностью существования уровня
        return Math.min(1.0, Math.log(1 + durationBars) / Math.log(50));
    }

    private double calculateAngleFactor(double angleRadians) {
        // Преобразуем угол в градусы для удобства
        double angleDegrees = Math.toDegrees(Math.abs(angleRadians));

        // Идеальный угол ~0° (горизонтальный уровень)
        // Углы > 10° считаем слишком крутыми
        if (angleDegrees > 10) {
            return 0.1; // Минимальная сила для крутых уровней
        }

        // Экспоненциальное затухание: чем ближе к 0, тем сила выше
        return Math.exp(-angleDegrees / 5.0);
    }

    private double calculateTouchFactor(int touchCount) {
        if (touchCount <= 0) return 0.0;
        if (touchCount <= 4) return 0.3 + 0.3 * (touchCount - 1);
        return Math.min(1.0, 0.9 + (touchCount - 5) * 0.02);
    }
}
