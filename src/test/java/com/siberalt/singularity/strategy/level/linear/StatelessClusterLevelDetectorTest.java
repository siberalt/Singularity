package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatelessClusterLevelDetectorTest {
    private final CandleFactory candleFactory = new CandleFactory("TEST_INSTRUMENT");

    @Test
    void detectReturnsEmptyListWhenCandlesIsNull() {
        StatelessClusterLevelDetector detector = StatelessClusterLevelDetector.createDefault(0.1, mock(ExtremeLocator.class));
        List<Level<Double>> result = detector.detect(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void detectReturnsEmptyListWhenCandlesIsEmpty() {
        StatelessClusterLevelDetector detector =  StatelessClusterLevelDetector.createDefault(0.1, mock(ExtremeLocator.class));
        List<Level<Double>> result = detector.detect(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void detectProcessesExtremesCorrectly() {
        ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 105.0),
            candleFactory.createCommon("2023-01-02T00:01:00Z", 108.0),
            candleFactory.createCommon("2023-01-02T00:02:00Z", 108.0),
            candleFactory.createCommon("2023-01-02T00:03:00Z", 107.0),
            candleFactory.createCommon("2023-01-02T00:04:00Z", 108.0),
            candleFactory.createCommon("2023-01-02T00:05:00Z", 107.0),
            candleFactory.createCommon("2023-01-02T00:06:00Z", 105.0)
        );
        when(extremeLocator.locate(candles)).thenReturn(candles);

        List<List<Candle>> expectedLevels = List.of(
            List.of(candles.get(0), candles.get(6)),
            List.of(candles.get(1), candles.get(2), candles.get(4)),
            List.of(candles.get(3), candles.get(5))
        );

        StatelessClusterLevelDetector detector =  StatelessClusterLevelDetector.createDefault(0.004, extremeLocator);
        List<Level<Double>> result = detector.detect(candles);

        assertLevels(expectedLevels, result);
        verify(extremeLocator, times(1)).locate(anyList());
    }

    @Test
    void detectLimitsLevelsToMaxLevels() {
        ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
        List<Candle> candles = List.of(
            candleFactory.createCommon("2023-01-01T00:00:00Z", 105.0),
            candleFactory.createCommon("2023-01-02T00:01:00Z", 105.0),
            candleFactory.createCommon("2023-01-02T00:02:00Z", 107.0),
            candleFactory.createCommon("2023-01-02T00:03:00Z", 107.0),
            candleFactory.createCommon("2023-01-02T00:04:00Z", 108.0),
            candleFactory.createCommon("2023-01-02T00:05:00Z", 108.0),
            candleFactory.createCommon("2023-01-02T00:06:00Z", 109.0),
            candleFactory.createCommon("2023-01-02T00:07:00Z", 109.0)
        );
        when(extremeLocator.locate(candles)).thenReturn(candles);

        StatelessClusterLevelDetector detector =  StatelessClusterLevelDetector.createDefault(0.01, extremeLocator, 3);
        List<Level<Double>> result = detector.detect(candles);

        assertEquals(2, result.size());
    }

    private void assertLevels(List<List<Candle>> expectedLevels, List<Level<Double>> actualLevels) {
        Set<Level<Double>> expectedLevelSet = levelsOfClusters(expectedLevels);

        Set<String> expectedSignatures = expectedLevelSet.stream()
            .map(level -> String.format("%s-%s-%d-%d-%s",
                level.timeFrom(),
                level.timeTo(),
                level.indexFrom(),
                level.indexTo(),
                level.function().toString())) // Use function(0.0) as a unique identifier for the function
            .collect(Collectors.toSet());

        Set<String> resultSignatures = actualLevels.stream()
            .map(level -> String.format("%s-%s-%d-%d-%s",
                level.timeFrom(),
                level.timeTo(),
                level.indexFrom(),
                level.indexTo(),
                level.function().toString()))
            .collect(Collectors.toSet());

        assertEquals(expectedSignatures, resultSignatures, "Levels do not match");
    }

    private Set<Level<Double>> levelsOfClusters(List<List<Candle>> clusters) {
        List<SortedSet<Candle>> clusterExtremes = new ArrayList<>();

        for (List<Candle> cluster : clusters) {
            TreeSet<Candle> sortedCluster = new TreeSet<>(Comparator.comparingDouble(Candle::getIndex));
            sortedCluster.addAll(cluster);
            clusterExtremes.add(sortedCluster);
        }

        Set<Level<Double>> levels = new HashSet<>();

        for (SortedSet<Candle> extremes : clusterExtremes) {
            double price = extremes
                .stream()
                .reduce(0.0, (sum, candle) -> sum + candle.getTypicalPriceAsDouble(), Double::sum) / extremes.size();
            levels.add(
                new Level<>(
                    extremes.first().getTime(),
                    extremes.last().getTime(),
                    extremes.first().getIndex(),
                    extremes.last().getIndex(),
                    StatelessClusterLevelDetector.createFunction(price),
                    0.0
                )
            );
        }

        return levels;
    }
}