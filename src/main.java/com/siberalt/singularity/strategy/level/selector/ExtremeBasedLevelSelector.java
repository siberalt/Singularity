package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.Collections;
import java.util.List;

// TODO is not finished. Needs fixing tests
public class ExtremeBasedLevelSelector implements LevelSelector {
    final double DEFAULT_EXTREME_VICINITY = 0.004;

    private final ExtremeLocator minimumLocator;
    private final ExtremeLocator maximumLocator;
    private double minimumVicinity = DEFAULT_EXTREME_VICINITY;
    private double maximumVicinity = DEFAULT_EXTREME_VICINITY;
    private PriceExtractor priceExtractor = Candle::getTypicalPrice;

    public ExtremeBasedLevelSelector(
        ExtremeLocator minimumLocator,
        ExtremeLocator maximumLocator
    ) {
        this.minimumLocator = minimumLocator;
        this.maximumLocator = maximumLocator;
    }

    public ExtremeBasedLevelSelector setPriceExtractor(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;
        return this;
    }

    public ExtremeBasedLevelSelector setMinimumVicinity(double minimumVicinity) {
        this.minimumVicinity = minimumVicinity;
        return this;
    }

    public ExtremeBasedLevelSelector setMaximumVicinity(double maximumVicinity) {
        this.maximumVicinity = maximumVicinity;
        return this;
    }

    @Override
    public List<LevelPair> select(
        List<Level<Double>> resistanceLevels,
        List<Level<Double>> supportLevels,
        List<Candle> recentCandles
    ) {
        List<Candle> minimums = minimumLocator.locate(recentCandles);
        List<Candle> maximums = maximumLocator.locate(recentCandles);

        if (minimums.isEmpty() || maximums.isEmpty()) {
            return Collections.emptyList();
        }

        Candle lastMinimum = minimums.get(minimums.size() - 1);
        Candle lastMaximum = maximums.get(maximums.size() - 1);

        List<Level<Double>> closestResistanceLevels = resistanceLevels.stream()
            .filter(level -> isWithinVicinity(level, lastMinimum, minimumVicinity))
            .toList();

        List<Level<Double>> closestSupportLevels = supportLevels.stream()
            .filter(level -> isWithinVicinity(level, lastMaximum, maximumVicinity))
            .toList();

        return closestSupportLevels.stream()
            .flatMap(support -> closestResistanceLevels.stream()
                .map(resistance -> new LevelPair(resistance, support)))
            .toList();
    }

    private boolean isWithinVicinity(Level<Double> level, Candle extreme, double vicinity) {
        double levelPrice = level.function().apply((double) extreme.getIndex());
        double extremePrice = priceExtractor.extract(extreme).toDouble();
        return Math.abs(levelPrice - extremePrice) / extremePrice <= vicinity;
    }
}
