package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;

import java.util.List;

public class SimpleLevelBasedSignalSource implements LevelBasedSignalSource {
    @Override
    public Signal calculate(LevelPair levelPair, List<Candle> recentCandles) {
        var resistance = levelPair.resistance();
        var support   = levelPair.support();

        long currentIndex = recentCandles.get(recentCandles.size() - 1).getIndex();
        double resistancePrice = resistance.function().apply((double) currentIndex);
        double supportPrice = support.function().apply((double) currentIndex);
        double currentPrice = recentCandles.get(recentCandles.size() - 1).getTypicalAsDouble();

        if (currentPrice > resistancePrice || currentPrice < supportPrice) {
            // Log a warning and return a neutral Signal
            System.err.println(
                "Warning: Current price is out of bounds defined by support and resistance levels. Returning neutral Signal."
            );
            return Signal.NEUTRAL;
        }

        double channelWidth = resistancePrice - supportPrice;
        double signal = 1 - (currentPrice - supportPrice) / channelWidth;
        double adaptedSignal = 2 * signal - 1;

        return new Signal(adaptedSignal, adaptedSignal);
    }
}
