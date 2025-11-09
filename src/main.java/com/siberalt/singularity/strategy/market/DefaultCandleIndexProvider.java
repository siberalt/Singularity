package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

public class DefaultCandleIndexProvider implements CumulativeCandleIndexProvider {
    private final HashMap<Instant, Long> candleIndexMap = new HashMap<>();
    private long currentIndex = 0L;

    @Override
    public long provideIndex(Candle candle) {
        return candleIndexMap.getOrDefault(candle.getTime(), NULL_INDEX);
    }

    @Override
    public void accumulate(List<Candle> candles) {
        long addedIndex = 0;
        for (Candle candle : candles) {
            Instant time = candle.getTime();

            if (!candleIndexMap.containsKey(time)) {
                candleIndexMap.put(time, addedIndex + currentIndex);
                addedIndex++;
            }
        }
        currentIndex += addedIndex;
    }
}
