package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class DBSCANClusterAggregator implements ClusterAggregator {
    private final double multiplier; // максимальное расстояние между точками в одном кластере (в ценах)
    private final int minPoints; // минимальное количество точек для формирования кластера
    private PriceExtractor priceExtractor = Candle::getClose;
    private MedianCalculator medianCalculator = new RobustMedianCalculator();

    public DBSCANClusterAggregator(double multiplier, int minPoints) {
        if (multiplier <= 0) throw new IllegalArgumentException("Multiplier must be positive");
        if (minPoints < 1) throw new IllegalArgumentException("minPoints must be at least 1");
        this.multiplier = multiplier;
        this.minPoints = minPoints;
    }

    public DBSCANClusterAggregator(double epsilon, int minPoints,
                                   PriceExtractor priceExtractor,
                                   MedianCalculator medianCalculator) {
        this(epsilon, minPoints);
        this.priceExtractor = priceExtractor;
        this.medianCalculator = medianCalculator;
    }

    @Override
    public List<Cluster> aggregate(List<Candle> extremes, double volatility) {
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

        for (int i = 0; i < prices.size(); i++) {
            if (!visited.get(i)) {
                visited.set(i, true);
                Set<Integer> neighbors = regionQuery(prices, i, volatility);
                if (neighbors.size() >= minPoints) {
                    Set<Integer> cluster = new HashSet<>();
                    expandCluster(prices, visited, isNoise, i, neighbors, cluster, volatility);
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
    private Set<Integer> regionQuery(List<Double> prices, int idx, double volatility) {
        Set<Integer> neighbors = new HashSet<>();
        double price = prices.get(idx);
        for (int i = 0; i < prices.size(); i++) {
            if (Math.abs(prices.get(i) - price) <= multiplier * volatility) {
                neighbors.add(i);
            }
        }
        return neighbors;
    }

    private void expandCluster(List<Double> prices, List<Boolean> visited, List<Boolean> isNoise,
                               int pointIdx, Set<Integer> seedNeighbors, Set<Integer> cluster, double volatility) {
        Queue<Integer> queue = new LinkedList<>(seedNeighbors);
        cluster.addAll(seedNeighbors);
        isNoise.set(pointIdx, false);

        while (!queue.isEmpty()) {
            Integer currentIdx = queue.poll();

            if (!visited.get(currentIdx)) {
                visited.set(currentIdx, true);
            }

            Set<Integer> currentNeighbors = regionQuery(prices, currentIdx, volatility);
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

    private boolean isInAnyCluster(Integer idx, Set<Integer> cluster) {
        return cluster.contains(idx);
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
}
