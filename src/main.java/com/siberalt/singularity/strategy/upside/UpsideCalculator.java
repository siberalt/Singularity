package com.siberalt.singularity.strategy.upside;

import java.time.Instant;

public interface UpsideCalculator {
    /**
     * Calculates the upside potential for a given instrument.
     *
     * @param instrumentId The ID of the instrument to calculate the upside for.
     * @return The calculated upside potential as a percentage in a range [-1, 1]
     */
    double calculate(String instrumentId, Instant currentTime);
}
