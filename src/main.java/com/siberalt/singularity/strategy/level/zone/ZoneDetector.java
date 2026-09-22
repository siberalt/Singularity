package com.siberalt.singularity.strategy.level.zone;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface ZoneDetector {
    /** Зоны окна, сильнейшая первой. */
    List<Zone> detect(List<Candle> candles);
}
