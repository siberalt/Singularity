package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;

public interface LevelBasedUpsideCalculator {
    Upside calculate(
        Level<Double> resistance,
        Level<Double> support,
        List<Candle> recentCandles,
        CandleIndexProvider candleIndexProvider
    );
}
