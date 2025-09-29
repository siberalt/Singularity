package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.math.LinearFunction2D;

public interface StrengthCalculator<T extends Number> {
    record LevelContext<T extends Number>(
        LinearFunction2D<T> linearFunction,
        long fromIndex,
        long toIndex,
        int touchesCount,
        long frameSize
    ) {
    }

    /**
     * Calculates the strength of a level based on touches count, linear function, and period.
     *
     * @param context The context containing the linear function, index range, touches count, and frame size.
     * @return The calculated strength of the level.
     */
    double calculate(LevelContext<T> context);
}
