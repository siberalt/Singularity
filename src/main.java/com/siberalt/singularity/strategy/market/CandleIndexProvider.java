package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

public interface CandleIndexProvider {
    long NULL_INDEX = -1;

    long provideIndex(Candle candle);
}
