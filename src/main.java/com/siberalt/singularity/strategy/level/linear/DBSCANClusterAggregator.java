package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class DBSCANClusterAggregator implements ClusterAggregator {
    private final double multiplier; // максимальное расстояние между точками в одном кластере (в ценах)
    private final int minPoints; // минимальное количество точек для формирования кластера
    private final int localVolatilityWindow; // минимальное количество точек для формирования кластера
    private final PriceExtractor priceExtractor;
    private final MedianCalculator medianCalculator;
    private final VolatilityCalculator volatiltyCalcualtor;
    private final ExtremeLocator extremeLocator;

    private DBSCANClusterAggregator(double multiplier, int minPoints,
                                    ExtremeLocator extremeLocator,
                                    int localVolatilityWindow,
                                    VolatilityCalculator volatilityCalculator,
                                    PriceExtractor priceExtractor,
                                    MedianCalculator medianCalculator) {
        this.multiplier = multiplier;
        this.minPoints = minPoints;
        this.extremeLocator = extremeLocator;
        this.volatiltyCalcualtor = volatilityCalculator;
        this.priceExtractor = priceExtractor;
        this.medianCalculator = medianCalculator;
        this.localVolatilityWindow = localVolatilityWindow;
    }

    @Override
    public List<Cluster> aggregate(List<Candle> lastCandles) {
        List<Candle> extremes = extremeLocator.locate(lastCandles);

        if (extremes == null || extremes.isEmpty()) {
            return List.of();
        }

        // Шаг 1: извлекаем цены
        List<Double> prices = extremes.stream()
            .map(c -> priceExtractor.extract(c).toDouble())
            .collect(toList());

        List<Boolean> visited = new ArrayList<>(Collections.nCopies(prices.size(), false));
        List<Boolean> isNoise = new ArrayList<>(Collections.nCopies(prices.size(), true));
        List<Set<Integer>> clustersIndices = new ArrayList<>();
        double[] localVolatilities = new double[prices.size()];
        long startIndex = lastCandles.getFirst().getIndex();

        for (int i = 0; i < localVolatilities.length; i++) {
            long extremeIndex = extremes.get(i).getIndex() - startIndex;
            int leftIndex = Math.toIntExact(Math.max(0, extremeIndex - localVolatilityWindow));
            int rightIndex = Math.toIntExact(Math.min(lastCandles.size(), extremeIndex + localVolatilityWindow + 1));
            localVolatilities[i] = volatiltyCalcualtor.calculate(lastCandles.subList(leftIndex, rightIndex));
        }

        for (int i = 0; i < prices.size(); i++) {
            if (!visited.get(i)) {
                visited.set(i, true);
                Set<Integer> neighbors = regionQuery(prices, i, localVolatilities);
                if (neighbors.size() >= minPoints) {
                    Set<Integer> cluster = new HashSet<>();
                    expandCluster(prices, visited, isNoise, i, neighbors, cluster, localVolatilities);
                    clustersIndices.add(cluster);
                }
            }
        }

        // Шаг 2: фильтруем по minPoints и создаем Cluster объекты
        return clustersIndices.stream()
            .filter(cluster -> cluster.size() >= minPoints)
            .map(indices -> {
                Set<Candle> clusterCandles = indices.stream()
                    .map(extremes::get)
                    .collect(toSet());

                RangeDouble range = calculateRange(clusterCandles);
                double center = calculateClusterPrice(clusterCandles);

                return new Cluster(center, clusterCandles, range);
            })
            .collect(toList());
    }

    // Находит все точки в окрестности epsilon от точки i
    private Set<Integer> regionQuery(List<Double> prices, int idx, double[] localVolatilities) {
        Set<Integer> neighbors = new HashSet<>();
        double price = prices.get(idx);
        double eps_i = multiplier * localVolatilities[idx];
        for (int i = 0; i < prices.size(); i++) {
            double eps_j = multiplier * localVolatilities[i];
            double eps_avg = (eps_i + eps_j) / 2;
            if (Math.abs(prices.get(i) - price) <= eps_avg) {
                neighbors.add(i);
            }
        }
        return neighbors;
    }

    private void expandCluster(List<Double> prices, List<Boolean> visited, List<Boolean> isNoise,
                               int pointIdx, Set<Integer> seedNeighbors, Set<Integer> cluster, double[] localVolatilities) {
        Queue<Integer> queue = new LinkedList<>(seedNeighbors);
        cluster.addAll(seedNeighbors);
        isNoise.set(pointIdx, false);

        while (!queue.isEmpty()) {
            Integer currentIdx = queue.poll();

            if (!visited.get(currentIdx)) {
                visited.set(currentIdx, true);
            }

            Set<Integer> currentNeighbors = regionQuery(prices, currentIdx, localVolatilities);
            if (currentNeighbors.size() >= minPoints) {
                for (Integer neighborIdx : currentNeighbors) {
                    if (!cluster.contains(neighborIdx)) {
                        cluster.add(neighborIdx);
                        queue.add(neighborIdx);
                        isNoise.set(neighborIdx, false);
                    }
                }
            }
        }
    }

    private RangeDouble calculateRange(Set<Candle> candles) {
        List<Double> prices = candles.stream()
            .map(c -> priceExtractor.extract(c).toDouble())
            .sorted()
            .toList();
        return new RangeDouble(prices.get(0), prices.get(prices.size() - 1));
    }

    private double calculateClusterPrice(Set<Candle> clusterCandles) {
        return medianCalculator.calculateMedian(
            clusterCandles.stream()
                .map(priceExtractor::extract)
                .map(Quotation::toDouble)
                .toList()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double multiplier = 0.5;
        private int minPoints = 2;
        private int localVolatilityWindow = 7;
        private PriceExtractor priceExtractor = Candle::close;
        private MedianCalculator medianCalculator = new RobustMedianCalculator();
        private VolatilityCalculator volatilityCalculator;
        private ExtremeLocator extremeLocator;

        public Builder multiplier(double multiplier) {
            if (multiplier <= 0) throw new IllegalArgumentException("Multiplier must be positive");
            this.multiplier = multiplier;
            return this;
        }

        public Builder minPoints(int minPoints) {
            if (minPoints < 1) throw new IllegalArgumentException("minPoints must be at least 1");
            this.minPoints = minPoints;
            return this;
        }

        public Builder localVolatilityWindow(int localVolatilityWindow) {
            if (localVolatilityWindow < 0) throw new IllegalArgumentException("localVolatilityWindow must be non-negative");
            this.localVolatilityWindow = localVolatilityWindow;
            return this;
        }

        public Builder priceExtractor(PriceExtractor priceExtractor) {
            if (priceExtractor == null) throw new IllegalArgumentException("priceExtractor cannot be null");
            this.priceExtractor = priceExtractor;
            return this;
        }

        public Builder medianCalculator(MedianCalculator medianCalculator) {
            if (medianCalculator == null) throw new IllegalArgumentException("medianCalculator cannot be null");
            this.medianCalculator = medianCalculator;
            return this;
        }

        public Builder volatilityCalculator(VolatilityCalculator volatilityCalculator) {
            if (volatilityCalculator == null) throw new IllegalArgumentException("volatilityCalculator cannot be null");
            this.volatilityCalculator = volatilityCalculator;
            return this;
        }

        public Builder extremeLocator(ExtremeLocator extremeLocator) {
            if (extremeLocator == null) throw new IllegalArgumentException("extremeLocator cannot be null");
            this.extremeLocator = extremeLocator;
            return this;
        }

        public DBSCANClusterAggregator build() {
            if (extremeLocator == null) {
                throw new IllegalStateException("extremeLocator must be set");
            }

            if (volatilityCalculator == null) {
                volatilityCalculator = new ATRVolatilityCalculator(localVolatilityWindow * 2);
            }
            return new DBSCANClusterAggregator(multiplier, minPoints, extremeLocator, localVolatilityWindow, volatilityCalculator, priceExtractor, medianCalculator);
        }
    }
}
