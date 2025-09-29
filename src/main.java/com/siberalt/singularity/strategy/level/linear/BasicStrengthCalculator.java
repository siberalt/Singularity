package com.siberalt.singularity.strategy.level.linear;

public class BasicStrengthCalculator implements StrengthCalculator<Double> {
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
    public double calculate(LevelContext<Double> context) {
        if (
            context.touchesCount() <= 0
                || context.linearFunction() == null
                || context.fromIndex() < 0
                || context.toIndex() < context.fromIndex()
        ) {
            return 0.0;
        }

        double slope = Math.abs(context.linearFunction().getSlope());
        double angleFactor = calculateAngleFactor(slope);
        double touchFactor = calculateTouchFactor(context.touchesCount());
        double timeframeFactor = calculateTimeframeFactor(context.toIndex() - context.fromIndex());

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
