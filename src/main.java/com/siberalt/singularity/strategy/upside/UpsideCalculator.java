package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface UpsideCalculator {
    /**
     * Calculates the upside potential for a given instrument.
     *
     * @param lastCandles The list of the most recent candles for the instrument
     * @return The calculated upside potential as a percentage in a range [-1, 1]
     */
    Upside calculate(List<Candle> lastCandles);
}
