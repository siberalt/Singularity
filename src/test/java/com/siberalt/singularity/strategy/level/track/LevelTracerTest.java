package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LevelTracerTest {
    private LevelTracer levelTracer;

    @BeforeEach
    void setUp() {
        levelTracer = new LevelTracer(0.1);
    }

    @Test
    void handlesSnapshotsWithOverlappingLevels() {
        TimePoint fromPoint1 = new TimePoint(0, null);
        TimePoint toPoint1 = new TimePoint(10, null);
        TimePoint fromPoint2 = new TimePoint(10, null);
        TimePoint toPoint2 = new TimePoint(20, null);

        Level<Double> level1 = new Level<>(0, 50, x -> 10.0);
        Level<Double> level2 = new Level<>(0, 50, x -> 10.5);

        SnapshotLevelGroup snapshot1 = new SnapshotLevelGroup(fromPoint1, toPoint1, List.of(level1));
        SnapshotLevelGroup snapshot2 = new SnapshotLevelGroup(fromPoint2, toPoint2, List.of(level2));

        LevelTraceGroup result = levelTracer.trace(List.of(snapshot1, snapshot2));

        assertNotNull(result);
        assertEquals(1, result.levelTraces().size());
        assertEquals(fromPoint1, result.fromPoint());
        assertEquals(toPoint2, result.toPoint());
    }

    @Test
    void handlesSnapshotsWithNonOverlappingLevels() {
        TimePoint fromPoint1 = new TimePoint(0);
        TimePoint toPoint1 = new TimePoint(10);
        TimePoint fromPoint2 = new TimePoint(10);
        TimePoint toPoint2 = new TimePoint(20);

        Level<Double> level1 = new Level<>(0, 50, x -> 10.0);
        Level<Double> level2 = new Level<>(0, 50, x -> 20.0);

        SnapshotLevelGroup snapshot1 = new SnapshotLevelGroup(fromPoint1, toPoint1, List.of(level1));
        SnapshotLevelGroup snapshot2 = new SnapshotLevelGroup(fromPoint2, toPoint2, List.of(level2));

        LevelTraceGroup result = levelTracer.trace(List.of(snapshot1, snapshot2));

        assertNotNull(result);
        assertEquals(2, result.levelTraces().size());
        assertEquals(fromPoint1, result.fromPoint());
        assertEquals(toPoint2, result.toPoint());
        assertEquals(fromPoint1, result.levelTraces().get(0).fromPoint());
        assertEquals(toPoint1, result.levelTraces().get(0).toPoint());
        assertEquals(fromPoint2, result.levelTraces().get(1).fromPoint());
        assertEquals(toPoint2, result.levelTraces().get(1).toPoint());
    }

    @Test
    void handlesEmptyLevelsInSnapshots() {
        TimePoint fromPoint = new TimePoint(0, null);
        TimePoint toPoint = new TimePoint(10, null);

        SnapshotLevelGroup snapshot = new SnapshotLevelGroup(fromPoint, toPoint, List.of());

        LevelTraceGroup result = levelTracer.trace(List.of(snapshot));

        assertNotNull(result);
        assertTrue(result.levelTraces().isEmpty());
        assertEquals(fromPoint, result.fromPoint());
        assertEquals(toPoint, result.toPoint());
    }
}