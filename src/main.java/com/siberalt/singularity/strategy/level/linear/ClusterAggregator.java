package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.*;
import java.util.stream.Collectors;

public class ClusterAggregator {
    private PriceExtractor priceExtractor = Candle::getTypicalPrice;
    private MedianCalculator medianCalculator = new RobustMedianCalculator();
    private final double sensitivity;

    public ClusterAggregator(double sensitivity) {
        this.sensitivity = sensitivity;
    }

    public ClusterAggregator(PriceExtractor priceExtractor, MedianCalculator medianCalculator, double sensitivity) {
        this.priceExtractor = priceExtractor;
        this.medianCalculator = medianCalculator;
        this.sensitivity = sensitivity;
    }

    public List<Cluster> aggregate(List<Candle> extremes) {
        NavigableMap<Double, Set<Candle>> extremesTree = createExtremesTree(extremes);
        List<Candle> sortedExtremes = extremes.stream()
            .sorted(Comparator.comparingDouble(candle -> priceExtractor.extract(candle).toDouble()))
            .toList();

        double clusterNeighborhoodWidth = sensitivity * 2;
        List<Cluster> clusters = new ArrayList<>();

        for (int i = 0; i < sortedExtremes.size(); i++) {
            Candle extreme = sortedExtremes.get(i);
            double priceA = priceExtractor.extract(extreme).toDouble();
            NavigableMap<Double, Set<Candle>> clusterSubMap = extremesTree.subMap(
                priceA, true, priceA + priceA * clusterNeighborhoodWidth, true
            );

            Set<Candle> clusterExtremes = clusterSubMap
                .values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

            if (clusterExtremes.size() > 1) {
                double clusterPrice = medianCalculator.calculateMedian(
                    clusterExtremes
                        .stream()
                        .map(priceExtractor::extract)
                        .map(Quotation::toDouble)
                        .toList()
                );
                RangeDouble clusterPriceRange = new RangeDouble(
                    clusterSubMap.firstKey(), clusterSubMap.lastKey()
                );
                clusters.add(new Cluster(clusterPrice, new HashSet<>(clusterExtremes), clusterPriceRange));
            } else if (clusterExtremes.isEmpty()) {
                continue;
            }

            i += clusterExtremes.size() - 1; // Пропускаем уже обработанные экстремумы
        }

        return clusters;
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
