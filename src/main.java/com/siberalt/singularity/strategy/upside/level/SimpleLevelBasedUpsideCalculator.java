package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;

public class SimpleLevelBasedUpsideCalculator implements LevelBasedUpsideCalculator {
    @Override
    public Upside calculate(
        Level<Double> resistance,
        Level<Double> support,
        List<Candle> recentCandles
    ) {
        long currentIndex = recentCandles.get(recentCandles.size() - 1).getIndex();
        double resistancePrice = resistance.function().apply((double) currentIndex);
        double supportPrice = support.function().apply((double) currentIndex);
        double currentPrice = recentCandles.get(recentCandles.size() - 1).getTypicalPrice().toDouble();

        if (currentPrice > resistancePrice || currentPrice < supportPrice) {
            // Log a warning and return a neutral Upside
            System.err.println(
                "Warning: Current price is out of bounds defined by support and resistance levels. Returning neutral Upside."
            );
            return Upside.NEUTRAL;
        }

        double channelWidth = resistancePrice - supportPrice;
        double upside = 1 - (currentPrice - supportPrice) / channelWidth;
        double adaptedUpside = 2 * upside - 1;

        return new Upside(adaptedUpside, adaptedUpside);
    }
}
