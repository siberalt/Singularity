package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;

public class ChannelLevelBasedUpsideCalculator implements LevelBasedUpsideCalculator {
    @Override
    public Upside calculate(
        Level<Double> resistance,
        Level<Double> support,
        List<Candle> recentCandles,
        CandleIndexProvider candleIndexProvider
    ) {
        long currentIndex = candleIndexProvider.provideIndex(recentCandles.get(recentCandles.size() - 1));
        double resistancePrice = resistance.function().apply((double) currentIndex);
        double supportPrice = support.function().apply((double) currentIndex);
        double resistanceStrength = resistance.strength();
        double supportStrength = support.strength();
        double currentPrice = recentCandles.get(recentCandles.size() - 1).getTypicalPrice().toDouble();

        if (currentPrice > resistancePrice || currentPrice < supportPrice) {
            // Log a warning and return a neutral Upside
            System.err.println(
                "Warning: Current price is out of bounds defined by support and resistance levels. Returning neutral Upside."
            );
            return Upside.NEUTRAL;
        }

        double channelWidth = resistancePrice - supportPrice;
        double upside;

        // Непрерывная функция от -1 (у поддержки) до +1 (у сопротивления)
        double normalizedPosition = (currentPrice - supportPrice) / channelWidth;
        double strengthRatio = resistanceStrength / (resistanceStrength + supportStrength);

        // С учетом силы уровней смещаем нейтральную точку
        double adjustedNeutralPoint = 0.5 * (1 + strengthRatio - supportStrength / (resistanceStrength + supportStrength));

        if (normalizedPosition <= adjustedNeutralPoint) {
            upside = 1 - (normalizedPosition / adjustedNeutralPoint) * 2; // от -1 до 1
        } else {
            upside = 1 - ((normalizedPosition - adjustedNeutralPoint) / (1 - adjustedNeutralPoint)) * 2; // от 1 до -1
        }

        return new Upside(Math.tanh(upside), upside);
    }
}
