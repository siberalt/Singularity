package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.strategy.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class LevelSelectorWindowTrackerTest {
    private LevelSelector baseSelectorMock;
    private LevelSelectorWindowTracker tracker;
    private final CandleFactory candleFactory = new CandleFactory("TEST");

    @BeforeEach
    void setUp() {
        baseSelectorMock = mock(LevelSelector.class);
        tracker = new LevelSelectorWindowTracker(baseSelectorMock);
    }

    @Test
    void returnsLevelPairsFromBaseSelector() {
        Level<Double> resistanceLevel = new Level<>(1, 3, x -> x);
        Level<Double> supportLevel = new Level<>(2, 4, x -> x - 1);
        List<Level<Double>> resistanceLevels = List.of(resistanceLevel);
        List<Level<Double>> supportLevels = List.of(supportLevel);
        List<Candle> recentCandles = List.of(
            candleFactory.createCommon("2021-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:01:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:02:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:03:00Z", 1)
        );
        List<LevelPair> expectedLevelPairs = List.of(new LevelPair(resistanceLevel, supportLevel));

        when(baseSelectorMock.select(resistanceLevels, supportLevels, recentCandles)).thenReturn(expectedLevelPairs);

        List<LevelPair> result = tracker.select(resistanceLevels, supportLevels, recentCandles);

        assertEquals(expectedLevelPairs, result);
    }

    @Test
    void tracksLevelPairsSnapshotWhenNewCandlesExist() {
        List<Candle> allCandles = List.of(
            candleFactory.createCommon("2021-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:01:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:02:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:03:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:04:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:05:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:06:00Z", 1)
        );
        Level<Double> resistance1 = new Level<>(1, 4, x -> x);
        Level<Double> support1 = new Level<>(1, 4, x -> x + 1);
        Level<Double> resistance2 = new Level<>(1, 6, x -> x * 2);
        Level<Double> support2 = new Level<>(1, 6, x -> x - 1);

        List<LevelPair> levelPairs1 = List.of(new LevelPair(resistance1, support1));
        List<LevelPair> levelPairs2 = List.of(new LevelPair(resistance2, support2));

        List<Candle> window1 = allCandles.subList(0, 4);
        List<Candle> window2 = allCandles.subList(3, 7);

        when(baseSelectorMock.select(any(), any(), eq(window1))).thenReturn(levelPairs1);
        when(baseSelectorMock.select(any(), any(), eq(window2))).thenReturn(levelPairs2);

        tracker.select(List.of(), List.of(), window1);
        tracker.select(List.of(), List.of(), window2);

        List<LevelPairsSnapshot> snapshots = tracker.getTrackedLevelPairs();
        assertEquals(2, snapshots.size());

        LevelPairsSnapshot snapshot = snapshots.get(0);
        assertEquals(0, snapshot.fromIndex());
        assertEquals(3, snapshot.toIndex());
        assertEquals(Instant.parse("2021-01-01T00:00:00Z"), snapshot.timeFrom());
        assertEquals(Instant.parse("2021-01-01T00:03:00Z"), snapshot.timeTo());
        assertEquals(levelPairs1, snapshot.levelPairs());

        snapshot = snapshots.get(1);
        assertEquals(4, snapshot.fromIndex());
        assertEquals(6, snapshot.toIndex());
        assertEquals(Instant.parse("2021-01-01T00:04:00Z"), snapshot.timeFrom());
        assertEquals(Instant.parse("2021-01-01T00:06:00Z"), snapshot.timeTo());
        assertEquals(levelPairs2, snapshot.levelPairs());
    }

    @Test
    void doesNotTrackWhenNoNewCandlesExist() {
        List<Candle> recentCandles = List.of(
            candleFactory.createCommon("2021-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2021-01-01T00:01:00Z", 1)
        );
        tracker.select(List.of(), List.of(), recentCandles);

        tracker.select(List.of(), List.of(), recentCandles);

        assertEquals(1, tracker.getTrackedLevelPairs().size());
    }

    @Test
    void handlesEmptyRecentCandlesGracefully() {
        List<LevelPair> result = tracker.select(List.of(), List.of(), List.of());

        assertTrue(result.isEmpty());
        assertTrue(tracker.getTrackedLevelPairs().isEmpty());
    }
}
