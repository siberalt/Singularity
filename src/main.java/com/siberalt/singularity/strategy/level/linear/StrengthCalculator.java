package com.siberalt.singularity.strategy.level.linear;

import java.time.Instant;
import java.util.function.Function;

public interface StrengthCalculator {
    record LevelContext(
        Instant timeFrom,
        Instant timeTo,
        long indexFrom,
        long indexTo,
        Function<Double, Double> function,
        double strength,
        int touchesCount
    ) {
    }

    /**
     * Calculates the strength of a level based on touches count, linear function, and period.
     *
     * @param context The context containing the linear function, index range, touches count, and frame size.
     * @return The calculated strength of the level.
     */
    double calculate(LevelContext context);
}
