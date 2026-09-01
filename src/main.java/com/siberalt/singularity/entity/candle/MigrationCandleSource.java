package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.List;

public interface MigrationCandleSource {
    List<Candle> getPeriod(String instrumentUid, Instant from, Instant to);

    CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to);
}
