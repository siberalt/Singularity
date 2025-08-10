package com.siberalt.singularity.strategy.level;

public class DoubleStrengthCalculator implements StrengthCalculator<Double> {
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

        // Пример простой формулы для расчета силы уровня
        return (context.touchesCount() * context.frameSize()) / (slope + 1e-3) ;
    }
}
