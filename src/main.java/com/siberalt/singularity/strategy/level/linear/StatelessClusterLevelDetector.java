package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.*;
import java.util.function.Function;

public class StatelessClusterLevelDetector implements LevelDetector {
    private static final int MAX_LEVELS = 30;
    private static final Map<Double, Function<Double, Double>> functionsCache = new WeakHashMap<>();
    private final ExtremeLocator extremeLocator;
    private StrengthCalculator strengthCalculator = new BasicStrengthCalculator();
    private final ClusterAggregator clusterAggregator;

    // Параметры для управления "забыванием" старых уровней
    private int maxLevels = MAX_LEVELS;

    public StatelessClusterLevelDetector(ExtremeLocator extremeLocator, ClusterAggregator clusterAggregator) {
        this.extremeLocator = extremeLocator;
        this.clusterAggregator = clusterAggregator;
    }

    public StatelessClusterLevelDetector(ExtremeLocator extremeLocator, ClusterAggregator clusterAggregator, int maxLevels) {
        this.extremeLocator = extremeLocator;
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

        // Обрабатываем только новые экстремумы
        List<Candle> extremes = extremeLocator.locate(candles);
        List<Cluster> clusters = clusterAggregator.aggregate(extremes);

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

            StrengthCalculator.LevelContext context = new StrengthCalculator.LevelContext(
                firstExtreme.getTime(),
                lastExtreme.getTime(),
                firstExtreme.getIndex(),
                lastExtreme.getIndex(),
                function,
                0.0, // Временная заглушка для силы, будет пересчитано ниже
                cluster.size()
            );

            Level<Double> updatedLevel = new Level<>(
                firstExtreme.getTime(),
                lastExtreme.getTime(),
                firstExtreme.getIndex(),
                lastExtreme.getIndex(),
                function,
                strengthCalculator.calculate(context)
            );
            levels.add(updatedLevel);
        }

        filterOutLevels(levels);

        return levels;
    }

    public static StatelessClusterLevelDetector createDefault(double sensitivity, ExtremeLocator extremeLocator) {
        return new StatelessClusterLevelDetector(extremeLocator, new ClusterAggregator(sensitivity));
    }

    public static StatelessClusterLevelDetector createDefault(double sensitivity, ExtremeLocator extremeLocator, PriceExtractor priceExtractor) {
        return new StatelessClusterLevelDetector(
            extremeLocator,
            new ClusterAggregator(priceExtractor, new RobustMedianCalculator(), sensitivity)
        );
    }

    public static StatelessClusterLevelDetector createDefault(double sensitivity, ExtremeLocator extremeLocator, int maxLevels) {
        return new StatelessClusterLevelDetector(
            extremeLocator,
            new ClusterAggregator(sensitivity),
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
