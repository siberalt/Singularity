package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Селектор, возвращающий топ N пар (сопротивление, поддержка) с наименьшим временным расстоянием
 * между индексами последнего касания (indexTo). При равном расстоянии выбирается пара с большей суммарной силой.
 */
public class StrongestLevelPairSelector implements LevelPairSelector {
    public static double DEFAULT_TIME_PROXIMITY_WEIGHT = 0.4;

    private final int maxPairsAmount;
    private final double minStrength;
    private final int combinationsPool;
    private final Long maxTimeDistance;
    private final double timeProximityWeight; // вес, с которым учитывается близость по времени

    public StrongestLevelPairSelector(
        int maxPairsAmount,
        double minStrength,
        int combinationsPool,
        Long maxTimeDistance,
        double timeProximityWeight
    ) {
        if (maxPairsAmount <= 0) throw new IllegalArgumentException("maxPairsAmount must be positive");
        this.maxPairsAmount = maxPairsAmount;
        this.minStrength = minStrength;
        this.combinationsPool = Math.max(combinationsPool, maxPairsAmount);
        this.maxTimeDistance = maxTimeDistance;
        this.timeProximityWeight = timeProximityWeight;
    }

    public StrongestLevelPairSelector(int maxPairsAmount, double minStrength, int combinationsPool, double timeProximityWeight) {
        this(maxPairsAmount, minStrength, combinationsPool, null, timeProximityWeight);
    }

    public StrongestLevelPairSelector(int maxPairsAmount, double minStrength, double timeProximityWeight) {
        this(maxPairsAmount, minStrength, maxPairsAmount * 2, null, timeProximityWeight);
    }

    public StrongestLevelPairSelector(int maxPairsAmount, double minStrength) {
        this(maxPairsAmount, minStrength, maxPairsAmount * 2, null, DEFAULT_TIME_PROXIMITY_WEIGHT);
    }

    public StrongestLevelPairSelector(int maxPairsAmount) {
        this(maxPairsAmount, 0.0, maxPairsAmount * 2, null, DEFAULT_TIME_PROXIMITY_WEIGHT);
    }

    @Override
    public List<LevelPair> select(List<Level<Double>> resistanceLevels,
                                  List<Level<Double>> supportLevels,
                                  List<Candle> recentCandles) {
        if (recentCandles == null || recentCandles.isEmpty()) {
            return List.of();
        }
        long currentIndex = recentCandles.get(recentCandles.size() - 1).getIndex();

        ToDoubleFunction<Level<Double>> keyExtractor = Level::strength;

        // Берём топ combinationsPool по силе
        List<Level<Double>> candidatesRes = resistanceLevels.stream()
            .filter(l -> l.strength() >= minStrength)
            .sorted(Comparator.comparingDouble(keyExtractor).reversed())
            .limit(combinationsPool)
            .toList();

        List<Level<Double>> candidatesSup = supportLevels.stream()
            .filter(l -> l.strength() >= minStrength)
            .sorted(Comparator.comparingDouble(keyExtractor).reversed())
            .limit(combinationsPool)
            .toList();

        List<LevelPairWithDistance> pairs = new ArrayList<>();

        for (Level<Double> resistance : candidatesRes) {
            for (Level<Double> support : candidatesSup) {
                if (isValidPair(resistance, support, currentIndex)) {
                    long timeDistance = calculateTimeDistance(resistance, support);

                    if (!isWithinMaxTimeDistance(timeDistance, maxTimeDistance)) {
                        continue;
                    }

                    double totalStrength = resistance.strength() + support.strength();

                    pairs.add(new LevelPairWithDistance(
                        new LevelPair(resistance, support),
                        timeDistance,
                        totalStrength
                    ));
                }
            }
        }

        // Сортировка: сперва минимальное временное расстояние, затем максимальная суммарная сила
        pairs.sort((a, b) -> {
            double scoreA = calculatePairScore(a);
            double scoreB = calculatePairScore(b);
            return Double.compare(scoreB, scoreA); // по убыванию
        });

        return pairs.stream()
            .limit(maxPairsAmount)
            .map(LevelPairWithDistance::pair)
            .collect(Collectors.toList());
    }

    private double calculatePairScore(LevelPairWithDistance levelPair) {
        return levelPair.totalStrength() + timeProximityWeight / (1.0 + levelPair.timeDistance());
    }

    /**
     * Проверяет, что уровень сопротивления выше уровня поддержки в указанной точке.
     */
    private boolean isValidPair(Level<Double> resistance, Level<Double> support, long index) {
        double resistanceValue = resistance.function().apply((double) index);
        double supportValue = support.function().apply((double) index);
        return resistanceValue > supportValue;
    }

    /**
     * Вычисляет временну́ю дистанцию между окончаниями уровней.
     */
    private long calculateTimeDistance(Level<Double> resistance, Level<Double> support) {
        return Math.abs(resistance.indexTo() - support.indexTo());
    }

    /**
     * Проверяет, что временная дистанция не превышает максимально допустимую.
     */
    private boolean isWithinMaxTimeDistance(long timeDistance, Long maxTimeDistance) {
        return maxTimeDistance == null || timeDistance <= maxTimeDistance;
    }

    private record LevelPairWithDistance(LevelPair pair, long timeDistance, double totalStrength) {
    }
}
