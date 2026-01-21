package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LevelSelectorTracker implements LevelSelector {
    private final LevelSelector baseSelector;

    private final List<LevelPairsSnapshot> trackedLevelPairs = new ArrayList<>();

    public LevelSelectorTracker(LevelSelector baseSelector) {
        this.baseSelector = baseSelector;
    }

    @Override
    public List<LevelPair> select(
        List<Level<Double>> resistanceLevels,
        List<Level<Double>> supportLevels,
        List<Candle> recentCandles
    ) {
        List<LevelPair> levelPairs = baseSelector.select(resistanceLevels, supportLevels, recentCandles);

        long fromIndex = recentCandles.get(0).getIndex();
        long toIndex = recentCandles.get(recentCandles.size() - 1).getIndex();
        Instant timeFrom = recentCandles.get(0).getTime();
        Instant timeTo = recentCandles.get(recentCandles.size() - 1).getTime();

        LevelPairsSnapshot snapshot = new LevelPairsSnapshot(
            fromIndex,
            toIndex,
            timeFrom,
            timeTo,
            levelPairs
        );
        trackedLevelPairs.add(snapshot);

        return levelPairs;
    }

    public List<LevelPairsSnapshot> getTrackedLevelPairs() {
        return trackedLevelPairs;
    }
}
