package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.*;
import java.util.stream.Collectors;

public class BaseClusterAggregator implements ClusterAggregator  {
    private static class ClusterExtremeGroup {
        private RangeDouble priceRange;
        private final Set<Candle> extremes;

        public ClusterExtremeGroup(RangeDouble priceRange, Set<Candle> extremes) {
            this.priceRange = priceRange;
            this.extremes = extremes;
        }

        public RangeDouble getPriceRange() {
            return priceRange;
        }

        public void setPriceRange(RangeDouble priceRange) {
            this.priceRange = priceRange;
        }

        public Set<Candle> getExtremes() {
            return extremes;
        }

        public int getSize() {
            return extremes.size();
        }
    }

    public static final int DEFAULT_CLUSTER_SIZE = 2;

    private PriceExtractor priceExtractor = Candle::getClosePrice;
    private MedianCalculator medianCalculator = new RobustMedianCalculator();
    private int minClusterSize = DEFAULT_CLUSTER_SIZE;
    private final double sensitivity;

    public BaseClusterAggregator(double sensitivity) {
        this.sensitivity = sensitivity;
    }

    public BaseClusterAggregator(double sensitivity, int minClusterSize) {
        this.sensitivity = sensitivity;
        this.minClusterSize = minClusterSize;
    }

    public BaseClusterAggregator(PriceExtractor priceExtractor, MedianCalculator medianCalculator, double sensitivity) {
        this.priceExtractor = priceExtractor;
        this.medianCalculator = medianCalculator;
        this.sensitivity = sensitivity;
    }

    @Override
    public List<Cluster> aggregate(List<Candle> extremes, double volatility) {
        NavigableMap<Double, Set<Candle>> extremesTree = createExtremesTree(extremes);
        List<Candle> sortedExtremes = extremes.stream()
            .sorted(Comparator.comparingDouble(candle -> priceExtractor.extract(candle).toDouble()))
            .toList();

        double clusterNeighborhoodWidth = sensitivity * 2;
        List<ClusterExtremeGroup> clusters = new ArrayList<>();
        ClusterExtremeGroup previousCluster = null;

        for (Candle extreme : sortedExtremes) {
            double priceA = priceExtractor.extract(extreme).toDouble();
            NavigableMap<Double, Set<Candle>> clusterSubMap = extremesTree.subMap(
                priceA, true, priceA + priceA * clusterNeighborhoodWidth, true
            );

            if (clusterSubMap.isEmpty()) {
                continue; // Нет экстремумов в этом диапазоне, пропускаем
            }

            RangeDouble newRange = new RangeDouble(clusterSubMap.firstKey(), clusterSubMap.lastKey());

            if (
                null != previousCluster && newRange.isSubsetOf(previousCluster.getPriceRange())
            ) {
                continue; // Новый кластер полностью внутри предыдущего, пропускаем его
            }

            Set<Candle> clusterExtremes = clusterSubMap
                .values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

            ClusterExtremeGroup newCluster = new ClusterExtremeGroup(newRange, clusterExtremes);
            clusters.add(newCluster);
            previousCluster = newCluster;
        }

        clusters = clusters.stream()
            .sorted(Comparator.comparingDouble(ClusterExtremeGroup::getSize).reversed())
            .collect(Collectors.toCollection(ArrayList::new));

        for (int i = 0; i < clusters.size(); i++) {
            ClusterExtremeGroup currentCluster = clusters.get(i);
            RangeDouble currentRange = currentCluster.getPriceRange();

            Iterator<ClusterExtremeGroup> iterator = clusters.listIterator(i + 1);

            while (iterator.hasNext()) {
                ClusterExtremeGroup nextCluster = iterator.next();
                RangeDouble nextRange = nextCluster.getPriceRange();
                RangeDouble intersectionRange = currentRange.intersection(nextRange);

                if (intersectionRange == null) {
                    continue; // Нет пересечения, пропускаем
                }

                Set<Candle> controversialExtremes = extremesTree
                    .subMap(
                        intersectionRange.fromIndex(), true, intersectionRange.toIndex(), true
                    )
                    .values()
                    .stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());

                // Удаляем экстремумы из следующего кластера, которые попадают в пересечение
                nextCluster.getExtremes().removeAll(controversialExtremes);

                if (nextCluster.getExtremes().size() < minClusterSize) {
                    iterator.remove(); // Если после удаления экстремумов в следующем кластере осталось меньше минимального размера, удаляем весь кластер
                } else {
                    nextCluster.setPriceRange(nextRange.subtract(intersectionRange)); // Обновляем диапазон следующего кластера
                }
            }
        }

        return clusters.stream().map(this::mapToCluster).toList();
    }

    private Cluster mapToCluster(ClusterExtremeGroup clusterGroup) {
        return new Cluster(
            calculateClusterPrice(clusterGroup.getExtremes()),
            clusterGroup.getExtremes(),
            clusterGroup.getPriceRange()
        );
    }

    private double calculateClusterPrice(Set<Candle> clusterExtremes) {
        return medianCalculator.calculateMedian(
            clusterExtremes
                .stream()
                .map(priceExtractor::extract)
                .map(Quotation::toDouble)
                .toList()
        );
    }

    private NavigableMap<Double, Set<Candle>> createExtremesTree(Collection<Candle> extremes) {
        TreeMap<Double, Set<Candle>> treeMap = new TreeMap<>();
        for (Candle extreme : extremes) {
            double price = priceExtractor.extract(extreme).toDouble();
            treeMap.computeIfAbsent(price, k -> new HashSet<>()).add(extreme);
        }
        return treeMap;
    }
}
