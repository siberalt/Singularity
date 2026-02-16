package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.Range;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LevelDetectorWindowTracker implements LevelDetector {
    private final LevelDetector wrappedDetector;
    private final List<LevelsSnapshot> snapshots = new ArrayList<>();
    private Range previousRange;

    public LevelDetectorWindowTracker(LevelDetector wrappedDetector) {
        this.wrappedDetector = wrappedDetector;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles) {
        List<Level<Double>> levels = wrappedDetector.detect(candles);

        if (candles.isEmpty()) {
            return levels; // No candles, nothing to track
        }

        Range newRange = new Range(
            candles.get(0).getIndex(),
            candles.get(candles.size() - 1).getIndex()
        );
        Range untrackedRange = null != previousRange ? newRange.subtract(previousRange) : newRange;

        if (null == untrackedRange) {
            return levels;// No new candles, nothing to track
        }

        long fromIndex = untrackedRange.getFromIndex();
        long toIndex = untrackedRange.getToIndex();
        Instant timeFrom = candles.get((int) (fromIndex - newRange.fromIndex())).getTime();
        Instant timeTo = candles.get((int) (toIndex - newRange.fromIndex())).getTime();

        previousRange = newRange;
        snapshots.add(new LevelsSnapshot(
            fromIndex,
            toIndex,
            timeFrom,
            timeTo,
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
