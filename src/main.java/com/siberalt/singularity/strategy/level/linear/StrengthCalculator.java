package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.TimePoint;

import java.time.Instant;
import java.util.function.Function;

public interface StrengthCalculator {
    record LevelContext(
        TimePoint pointFrom,
        TimePoint pointTo,
        Function<Double, Double> function,
        double strength,
        int touchesCount
    ) {
        public long indexFrom() {
            return pointFrom.index();
        }

        public long indexTo() {
            return pointTo.index();
        }

        public Instant timeFrom() {
            return pointFrom.time();
        }

        public Instant timeTo() {
            return pointTo.time();
        }
    }

    /**
     * Calculates the strength of a level based on touches count, linear function, and period.
     *
     * @param context The context containing the linear function, index range, touches count, and frame size.
     * @return The calculated strength of the level.
     */
    double calculate(LevelContext context);
}
