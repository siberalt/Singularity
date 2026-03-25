package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LevelDetectorTracker implements LevelDetector {
    private final LevelDetector wrappedDetector;
    private final List<SnapshotLevelGroup> snapshots = new ArrayList<>();

    public LevelDetectorTracker(LevelDetector wrappedDetector) {
        this.wrappedDetector = wrappedDetector;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles) {
        List<Level<Double>> levels = wrappedDetector.detect(candles);
        Candle firstCandle = candles.get(0);
        Candle lastCandle = candles.get(candles.size() - 1);

        snapshots.add(
            new SnapshotLevelGroup(
                new TimePoint(firstCandle.getIndex(), firstCandle.getTime()),
                new TimePoint(lastCandle.getIndex(), lastCandle.getTime()),
                levels
            )
        );

        return levels;
    }

    public List<SnapshotLevelGroup> getSnapshots() {
        return snapshots;
    }

    public Optional<SnapshotLevelGroup> getLastSnapshot() {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snapshots.get(snapshots.size() - 1));
    }
}
