package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.strength.SimpleStrengthCalculator;
import com.siberalt.singularity.strategy.level.strength.StrengthCalculator;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.*;
import java.util.function.Function;

public class StatelessClusterLevelDetector implements LevelDetector {
    private static final int MAX_LEVELS = 30;
    private static final Map<Double, Function<Double, Double>> functionsCache = new WeakHashMap<>();
    private StrengthCalculator strengthCalculator = new SimpleStrengthCalculator();
    private final ClusterAggregator clusterAggregator;

    // Параметры для управления "забыванием" старых уровней
    private int maxLevels = MAX_LEVELS;

    public StatelessClusterLevelDetector(ClusterAggregator clusterAggregator) {
        this.clusterAggregator = Objects.requireNonNull(clusterAggregator);
    }

    public StatelessClusterLevelDetector(ClusterAggregator clusterAggregator, int maxLevels) {
        this.clusterAggregator = clusterAggregator;
        this.maxLevels = maxLevels;
    }

    public StatelessClusterLevelDetector setStrengthCalculator(StrengthCalculator strengthCalculator) {
        this.strengthCalculator = strengthCalculator;
        return this;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return Collections.emptyList(); // Нет данных, возвращаем пустой список
        }

        List<Cluster> clusters = clusterAggregator.aggregate(candles);

        List<Level<Double>> levels = new ArrayList<>();

        for (Cluster cluster : clusters) {
            if (!isSignificantCluster(cluster)) {
                continue; // Пропускаем кластеры, которые не являются значимыми уровнями
            }

            SortedSet<Candle> clusterExtremes = new TreeSet<>(Comparator.comparing(Candle::getIndex));
            clusterExtremes.addAll(cluster.extremes());

            Candle firstExtreme = clusterExtremes.first();
            Candle lastExtreme = clusterExtremes.last();

            Function<Double, Double> function = createFunction(cluster.price());
            TimePoint pointFrom = new TimePoint(firstExtreme.getIndex(), firstExtreme.getTime());
            TimePoint pointTo = new TimePoint(lastExtreme.getIndex(), lastExtreme.getTime());

            Level<Double> updatedLevel = new Level<>(
                pointFrom,
                pointTo,
                function,
                0.0,
                cluster.size()
            );
            double strength = strengthCalculator.calculate(updatedLevel, candles);
            updatedLevel = updatedLevel.withStrength(strength);
            levels.add(updatedLevel);
        }

        filterOutLevels(levels);

        return levels;
    }

    public static StatelessClusterLevelDetector createDefault(
        double multiplier,
        ExtremeLocator extremeLocator,
        int localVolatilityWindow
    ) {
        return new StatelessClusterLevelDetector(
            DBSCANClusterAggregator
                .builder()
                .localVolatilityWindow(localVolatilityWindow)
                .multiplier(multiplier)
                .extremeLocator(extremeLocator)
                .build()
        );
    }

    public static StatelessClusterLevelDetector createDefault(
        double multiplier,
        ExtremeLocator extremeLocator
    ) {
        return new StatelessClusterLevelDetector(
            DBSCANClusterAggregator
                .builder()
                .multiplier(multiplier)
                .extremeLocator(extremeLocator)
                .build()
        );
    }

    public static StatelessClusterLevelDetector createDefault(ExtremeLocator extremeLocator, PriceExtractor priceExtractor) {
        return new StatelessClusterLevelDetector(
            DBSCANClusterAggregator
                .builder()
                .extremeLocator(extremeLocator)
                .priceExtractor(priceExtractor)
                .build()
        );
    }

    public static StatelessClusterLevelDetector createDefault(ExtremeLocator extremeLocator, int maxLevels) {
        return new StatelessClusterLevelDetector(
            DBSCANClusterAggregator
                .builder()
                .extremeLocator(extremeLocator)
                .build(),
            maxLevels
        );
    }

    public static Function<Double, Double> createFunction(double price) {
        return functionsCache.computeIfAbsent(price, p -> x -> p);
    }

    /**
     * Очищает устаревшие уровни и уровни с низкой значимостью
     */
    private void filterOutLevels(List<Level<Double>> levels) {
        // Если все еще слишком много уровней, удаляем самые слабые
        if (levels.size() > maxLevels) {
            removeWeakestLevels(levels, levels.size() - maxLevels);
        }
    }

    private boolean isSignificantCluster(Cluster cluster) {
        return cluster.size() >= 2; // Минимум 2 экстремума для формирования уровня
    }

    private void removeWeakestLevels(List<Level<Double>> levels, int countToRemove) {
        levels
            .stream()
            .sorted(Comparator.comparingDouble(Level::strength))
            .limit(countToRemove)
            .forEach(levels::remove);
    }
}
