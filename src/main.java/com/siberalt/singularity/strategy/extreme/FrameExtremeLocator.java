package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FrameExtremeLocator implements ExtremeLocator {
    private final long frameSize;
    private final ExtremeLocator baseLocator;

    public FrameExtremeLocator(long frameSize, ExtremeLocator baseLocator) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        List<Candle> extremeList = new ArrayList<>();
        ArrayList<Candle> currentFrameCandles = new ArrayList<>();
        Instant startFrameTime = null;

        for (var candle : candles) {
            if (null == startFrameTime) {
                startFrameTime = candle.getTime();
            }

            currentFrameCandles.add(candle);

            if (currentFrameCandles.size() == frameSize) {
                extremeList.addAll(baseLocator.locate(currentFrameCandles));
                startFrameTime = null;
                currentFrameCandles.clear();
            }
        }

        if (!currentFrameCandles.isEmpty()) {
            extremeList.addAll(baseLocator.locate(currentFrameCandles));
        }

        return extremeList;
    }
}
