package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.List;

public interface MigrationCandleSource {
    List<Candle> getPeriod(long instrumentId, Instant from, Instant to);

    CandleRangeMetadata getRangeMetadata(long instrumentId, Instant from, Instant to);
}
