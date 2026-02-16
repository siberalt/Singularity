package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LevelDetectorWindowTrackerTest {
    private LevelDetector wrappedDetectorMock;
    private LevelDetectorWindowTracker tracker;
    private final CandleFactory candleFactory = new CandleFactory("TEST");

    @BeforeEach
    void setUp() {
        wrappedDetectorMock = mock(LevelDetector.class);
        tracker = new LevelDetectorWindowTracker(wrappedDetectorMock);
    }

    @Test
    void returnsLevelsFromWrappedDetector() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 1)
        );
        List<Level<Double>> expectedLevels = List.of(new Level<>(1, 20, x -> x + 10));

        when(wrappedDetectorMock.detect(candles)).thenReturn(expectedLevels);

        List<Level<Double>> result = tracker.detect(candles);

        assertEquals(expectedLevels, result);
    }

    @Test
    void tracksSnapshotWhenNewCandlesExist() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:03:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:04:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:05:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:06:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:07:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:08:00Z", 1)
        );
        List<Candle> window1 = candles.subList(0, 4);
        List<Candle> window2 = candles.subList(2, 6);
        List<Level<Double>> levels1 = List.of(
            new Level<>(1, 20, x -> x + 10),
            new Level<>(0, 10, x -> x + 5)
        );
        List<Level<Double>> levels2 = List.of(
            new Level<>(2, 30, x -> x + 15),
            new Level<>(1, 15, x -> x + 7)
        );

        when(wrappedDetectorMock.detect(window1)).thenReturn(levels1);
        when(wrappedDetectorMock.detect(window2)).thenReturn(levels2);

        tracker.detect(window1);
        tracker.detect(window2);

        List<LevelsSnapshot> snapshots = tracker.getSnapshots();
        assertEquals(2, snapshots.size());

        LevelsSnapshot snapshot = snapshots.get(0);
        assertEquals(0, snapshot.fromIndex());
        assertEquals(3, snapshot.toIndex());
        assertEquals(levels1, snapshot.levels());

        snapshot = snapshots.get(1);
        assertEquals(4, snapshot.fromIndex());
        assertEquals(5, snapshot.toIndex());
        assertEquals(levels2, snapshot.levels());
    }

    @Test
    void doesNotTrackSnapshotWhenNoNewCandlesExist() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 1)
        );

        tracker.detect(candles);
        tracker.detect(candles);

        assertEquals(1, tracker.getSnapshots().size());
    }

    @Test
    void handlesEmptyCandlesGracefully() {
        List<Level<Double>> result = tracker.detect(List.of());

        assertTrue(result.isEmpty());
        assertTrue(tracker.getSnapshots().isEmpty());
    }

    @Test
    void returnsLastSnapshotWhenSnapshotsExist() {
        List<Candle> candles = List.of(
            candleFactory.createCommon("2024-01-01T00:00:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:01:00Z", 1),
            candleFactory.createCommon("2024-01-01T00:02:00Z", 1)
        );
        List<Level<Double>> levels = List.of(new Level<>(1, 20, x -> x + 10));

        when(wrappedDetectorMock.detect(candles)).thenReturn(levels);

        tracker.detect(candles);

        Optional<LevelsSnapshot> lastSnapshot = tracker.getLastSnapshot();
        assertTrue(lastSnapshot.isPresent());
        assertEquals(0, lastSnapshot.get().fromIndex());
        assertEquals(2, lastSnapshot.get().toIndex());
    }

    @Test
    void returnsEmptyOptionalWhenNoSnapshotsExist() {
        Optional<LevelsSnapshot> lastSnapshot = tracker.getLastSnapshot();

        assertTrue(lastSnapshot.isEmpty());
    }
}
