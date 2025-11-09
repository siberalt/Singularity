package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface CumulativeCandleIndexProvider extends CandleIndexProvider {
    void accumulate(List<Candle> candles);
}
