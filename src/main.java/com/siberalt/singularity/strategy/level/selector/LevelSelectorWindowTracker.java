package com.siberalt.singularity.strategy.level.selector;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.Range;
import com.siberalt.singularity.strategy.level.Level;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LevelSelectorWindowTracker implements LevelSelector {
    private final LevelSelector baseSelector;
    private final List<LevelPairsSnapshot> trackedLevelPairs = new ArrayList<>();
    private Range previousRange;

    public LevelSelectorWindowTracker(LevelSelector baseSelector) {
        this.baseSelector = baseSelector;
    }

    @Override
    public List<LevelPair> select(
        List<Level<Double>> resistanceLevels,
        List<Level<Double>> supportLevels,
        List<Candle> recentCandles
    ) {
        List<LevelPair> levelPairs = baseSelector.select(resistanceLevels, supportLevels, recentCandles);

        if (recentCandles.isEmpty()) {
            return levelPairs; // No candles, nothing to track
        }

        Range newRange = new Range(
            recentCandles.get(0).getIndex(),
            recentCandles.get(recentCandles.size() - 1).getIndex()
        );
        Range untrackedRange = null != previousRange ? newRange.subtract(previousRange) : newRange;

        if (null == untrackedRange) {
            return levelPairs;// No new candles, nothing to track
        }

        long fromIndex = untrackedRange.getFromIndex();
        long toIndex = untrackedRange.getToIndex();
        Instant timeFrom = recentCandles.get((int) (fromIndex - newRange.fromIndex())).getTime();
        Instant timeTo = recentCandles.get((int) (toIndex - newRange.fromIndex())).getTime();

        LevelPairsSnapshot snapshot = new LevelPairsSnapshot(
            fromIndex,
            toIndex,
            timeFrom,
            timeTo,
            levelPairs
        );
        previousRange = newRange;
        trackedLevelPairs.add(snapshot);

        return levelPairs;
    }

    public List<LevelPairsSnapshot> getTrackedLevelPairs() {
        return trackedLevelPairs;
    }
}
