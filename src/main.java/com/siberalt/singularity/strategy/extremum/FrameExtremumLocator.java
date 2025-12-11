package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FrameExtremumLocator implements ExtremumLocator {
    private final long frameSize;
    private final ExtremumLocator baseLocator;

    public FrameExtremumLocator(long frameSize, ExtremumLocator baseLocator) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
    }

    @Override
    public List<Candle> locate(List<Candle> candles, CandleIndexProvider candleIndexProvider) {
        List<Candle> extremumList = new ArrayList<>();
        ArrayList<Candle> currentFrameCandles = new ArrayList<>();
        Instant startFrameTime = null;

        for (var candle : candles) {
            if (null == startFrameTime) {
                startFrameTime = candle.getTime();
            }

            currentFrameCandles.add(candle);

            if (currentFrameCandles.size() == frameSize) {
                extremumList.addAll(baseLocator.locate(currentFrameCandles, candleIndexProvider));
                startFrameTime = null;
                currentFrameCandles.clear();
            }
        }

        if (!currentFrameCandles.isEmpty()) {
            extremumList.addAll(baseLocator.locate(currentFrameCandles, candleIndexProvider));
        }

        return extremumList;
    }
}
