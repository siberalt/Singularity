package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.time.Instant;
import java.util.List;

public interface UpsideCalculator {
    /**
     * Calculates the upside potential for a given instrument.
     *
     * @param instrumentId The ID of the instrument to calculate the upside for.
     * @return The calculated upside potential as a percentage in a range [-1, 1]
     */
    Upside calculate(String instrumentId, Instant currentTime, List<Candle> lastCandles);
}
