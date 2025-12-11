package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import java.util.List;

public class NullUpsideCalculator implements UpsideCalculator {
    @Override
    public Upside calculate(List<Candle> recentCandles) {
        return new Upside(0, 0);
    }
}
