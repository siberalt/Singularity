package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ExtremeBasedLevelSelector implements LevelSelector {
    public final double DEFAULT_VICINITY_MULTIPLIER = 1.0;
    public final int DEFAULT_NUMBER_OF_PAIRS = 3;

    private final ExtremeLocator minimumLocator;
    private final ExtremeLocator maximumLocator;
    private PriceExtractor priceExtractor = Candle::getTypicalPrice;
    private int numberOfPairs = DEFAULT_NUMBER_OF_PAIRS;
    private final VolatilityCalculator volatilityCalculator;
    private double vicinityMultiplier = DEFAULT_VICINITY_MULTIPLIER;   // сколько волатильностей допускать

    public ExtremeBasedLevelSelector(
        ExtremeLocator minimumLocator,
        ExtremeLocator maximumLocator,
        VolatilityCalculator volatilityCalculator
    ) {
        this.minimumLocator = minimumLocator;
        this.maximumLocator = maximumLocator;
        this.volatilityCalculator = volatilityCalculator;
    }

    public ExtremeBasedLevelSelector(
        ExtremeLocator minimumLocator,
        ExtremeLocator maximumLocator
    ) {
        this.minimumLocator = minimumLocator;
        this.maximumLocator = maximumLocator;
        this.volatilityCalculator = new ATRVolatilityCalculator();
    }

    public void setVicinityMultiplier(double vicinityMultiplier) {
        this.vicinityMultiplier = vicinityMultiplier;
    }

    public void setNumberOfPairs(int numberOfPairs) {
        this.numberOfPairs = numberOfPairs;
    }

    public ExtremeBasedLevelSelector setPriceExtractor(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;
        return this;
    }

    @Override
    public List<LevelPair> select(
        List<Level<Double>> resistanceLevels,
        List<Level<Double>> supportLevels,
        List<Candle> recentCandles
    ) {
        if (resistanceLevels == null || supportLevels == null || recentCandles == null) {
            return Collections.emptyList();
        }

        List<Candle> minimums = minimumLocator.locate(recentCandles);
        List<Candle> maximums = maximumLocator.locate(recentCandles);

        if (minimums.isEmpty() || maximums.isEmpty()) {
            return Collections.emptyList();
        }

        // Предполагаем, что списки уже отсортированы по индексу (требование к локаторам)
        Candle lastMin = minimums.get(minimums.size() - 1);
        Candle lastMax = maximums.get(maximums.size() - 1);

        double volatility = volatilityCalculator.calculate(recentCandles);
        double dynamicVicinity = volatility * vicinityMultiplier;

        List<Level<Double>> topResistances = findTopNLevels(resistanceLevels, lastMax, dynamicVicinity, numberOfPairs);
        List<Level<Double>> topSupports = findTopNLevels(supportLevels, lastMin, dynamicVicinity, numberOfPairs);

        List<LevelPair> result = new ArrayList<>();
        for (Level<Double> res : topResistances) {
            for (Level<Double> sup : topSupports) {
                result.add(new LevelPair(res, sup));
            }
        }
        return result;
    }

    private boolean isWithinVicinity(Level<Double> level, Candle extreme, double absoluteTolerance) {
        double extremePrice = priceExtractor.extract(extreme).toDouble();
        if (Math.abs(extremePrice) < 1e-9) {
            return false;
        }

        double levelPrice = level.function().apply((double) extreme.getIndex());
        return Math.abs(levelPrice - extremePrice) <= absoluteTolerance;
    }


    private List<Level<Double>> findTopNLevels(List<Level<Double>> levels, Candle extreme, double vicinity, int n) {
        double extremumPrice = priceExtractor.extract(extreme).toDouble();

        return levels.stream()
            .filter(level -> isWithinVicinity(level, extreme, vicinity))
            .sorted(Comparator.comparingDouble(level ->
                Math.abs(level.function().apply((double) extreme.getIndex()) - extremumPrice)))
            .limit(n)
            .collect(Collectors.toList());
    }
}
