package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LevelDetectorTracker implements LevelDetector {
    private final LevelDetector wrappedDetector;
    private final List<LevelsSnapshot> snapshots = new ArrayList<>();

    public LevelDetectorTracker(LevelDetector wrappedDetector) {
        this.wrappedDetector = wrappedDetector;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles) {
        List<Level<Double>> levels = wrappedDetector.detect(candles);
        Candle firstCandle = candles.get(0);
        Candle lastCandle = candles.get(candles.size() - 1);

        snapshots.add(new LevelsSnapshot(
            firstCandle.getIndex(),
            lastCandle.getIndex(),
            firstCandle.getTime(),
            lastCandle.getTime(),
            levels
        ));
        return levels;
    }

    public List<LevelsSnapshot> getSnapshots() {
        return snapshots;
    }

    public Optional<LevelsSnapshot> getLastSnapshot() {
        if (snapshots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snapshots.get(snapshots.size() - 1));
    }
}
