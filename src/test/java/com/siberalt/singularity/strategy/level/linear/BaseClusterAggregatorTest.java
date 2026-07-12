package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.*;

class BaseClusterAggregatorTest {
    private final CandleFactory candleFactory = new CandleFactory("TEST");

    @Test
    void aggregatesClustersWithMultipleExtremes() {
        ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
        List<Candle> extremes = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100.0),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 101.0),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 102.0),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 200.0),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 201.0)
        );
        when(extremeLocator.locate(any())).thenReturn(extremes);

        BaseClusterAggregator aggregator = new BaseClusterAggregator(0.1, extremeLocator);


        List<Cluster> resultClusters = aggregator.aggregate(extremes);

        assertEquals(2, resultClusters.size());

        assertClustersEquals(
            List.of(
                new Cluster(101.0, Set.of(extremes.get(0), extremes.get(1), extremes.get(2)), new RangeDouble(100.0, 102.0)),
                new Cluster(200.5, Set.of(extremes.get(3), extremes.get(4)), new RangeDouble(200.0, 201.0))
            ),
            resultClusters
        );
    }

    @Test
    void aggregatesClustersWithEqualExtremes() {
        ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
        List<Candle> extremes = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100.0),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 100.0)
        );
        when(extremeLocator.locate(any())).thenReturn(extremes);

        BaseClusterAggregator aggregator = new BaseClusterAggregator(0.1, extremeLocator);

        List<Cluster> result = aggregator.aggregate(extremes);

        assertClustersEquals(
            List.of(
                new Cluster(100.0, Set.of(extremes.get(0), extremes.get(1)), new RangeDouble(100.0, 100.0))
            ),
            result
        );
    }

    @Test
    void handlesEmptyExtremesList() {
        ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
        List<Candle> extremes = Collections.emptyList();
        when(extremeLocator.locate(any())).thenReturn(extremes);

        BaseClusterAggregator aggregator = new BaseClusterAggregator(0.1, extremeLocator);

        List<Cluster> clusters = aggregator.aggregate(extremes);

        assertTrue(clusters.isEmpty());
    }

    @Test
    void handlesClustersWithNoSubsetRelationsUsingAggregate() {
        ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
        List<Candle> extremes = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 100.0),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 200.0),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 100.0),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 200.0)
        );
        when(extremeLocator.locate(any())).thenReturn(extremes);

        BaseClusterAggregator aggregator = new BaseClusterAggregator(0.1, extremeLocator);

        List<Cluster> result = aggregator.aggregate(extremes);

        assertEquals(2, result.size());
        assertClustersEquals(
            List.of(
                new Cluster(100.0, Set.of(extremes.get(0), extremes.get(2)), new RangeDouble(100.0, 100.0)),
                new Cluster(200.0, Set.of(extremes.get(1), extremes.get(3)), new RangeDouble(200.0, 200.0))
            ),
            result
        );
    }

    private void assertClustersEquals(List<Cluster> expected, List<Cluster> actual) {
        assertEquals(expected.size(), actual.size(), "Cluster lists have different sizes");

        for (Cluster cluster : expected) {
            Cluster matchingCluster = actual.stream()
                .filter(c -> Math.abs(c.price() - cluster.price()) < 0.001)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cluster with price " + cluster.price() + " not found in actual clusters"));
            assertClusterEquals(cluster, matchingCluster);
        }
    }

    private void assertClusterEquals(Cluster expected, Cluster actual) {
        assertEquals(expected.price(), actual.price(), 0.001);
        assertEquals(expected.extremes(), actual.extremes());
        assertEquals(expected.priceRange(), actual.priceRange());
    }
}
