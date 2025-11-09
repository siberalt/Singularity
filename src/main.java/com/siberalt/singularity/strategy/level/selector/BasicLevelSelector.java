package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.util.Comparator;
import java.util.List;

public class BasicLevelSelector implements LevelSelector {
    private int limit = 1; // Default limit

    public BasicLevelSelector(int limit) {
        this.limit = limit;
    }

    public BasicLevelSelector() {
    }

    @Override
    public List<LevelPair> select(
        List<Level<Double>> resistanceLevels,
        List<Level<Double>> supportLevels,
        List<Candle> recentCandles,
        CandleIndexProvider candleIndexProvider
    ) {
        Candle currentCandle = recentCandles.get(recentCandles.size() - 1);
        double currentPrice = currentCandle.getTypicalPrice().toDouble();
        long currentIndex = candleIndexProvider.provideIndex(currentCandle);

        List<Level<Double>> closestResistanceLevels = resistanceLevels.stream()
            .filter(level -> level.function().apply((double) currentIndex) > currentPrice)
            .sorted(Comparator
                .comparingDouble((Level<Double> l) -> Math.abs(l.function().apply((double) currentIndex) - currentPrice))
                .thenComparingLong((Level<Double> l) -> -l.indexTo())
            )
            .limit(limit)
            .toList();

        List<Level<Double>> closestSupportLevels = supportLevels.stream()
            .filter(level -> level.function().apply((double) currentIndex) < currentPrice)
            .sorted(Comparator
                .comparingDouble((Level<Double> l) -> Math.abs(l.function().apply((double) currentIndex) - currentPrice))
                .thenComparingLong((Level<Double> l) -> -l.indexTo())
            )
            .limit(limit)
            .toList();

        return uniteIntersectingLevels(closestSupportLevels, closestResistanceLevels);
    }

    private List<LevelPair> uniteIntersectingLevels(
        List<Level<Double>> supportLevels,
        List<Level<Double>> resistanceLevels
    ) {
        return supportLevels.stream()
            .flatMap(support -> resistanceLevels.stream()
                .filter(resistance -> resistance.intersects(support))
                .map(resistance -> new LevelPair(resistance, support)))
            .toList();
    }
}
