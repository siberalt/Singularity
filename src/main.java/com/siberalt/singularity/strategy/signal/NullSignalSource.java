package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import java.util.List;

public class NullSignalSource implements SignalSource {
    @Override
    public Signal calculate(List<Candle> recentCandles) {
        return new Signal(0, 0);
    }
}
