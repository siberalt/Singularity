package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LevelDetectorTracker implements LevelDetector<Double> {
    private final LevelDetector<Double> wrappedDetector;
    private final List<LevelsSnapshot> snapshots = new ArrayList<>();

    public LevelDetectorTracker(LevelDetector<Double> wrappedDetector) {
        this.wrappedDetector = wrappedDetector;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles, CandleIndexProvider candleIndexProvider) {
        List<Level<Double>> levels = wrappedDetector.detect(candles, candleIndexProvider);
        snapshots.add(new LevelsSnapshot(
            candleIndexProvider.provideIndex(candles.get(0)),
            candleIndexProvider.provideIndex(candles.get(candles.size() - 1)),
            candles.get(0).getTime(),
            candles.get(candles.size() - 1).getTime(),
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
