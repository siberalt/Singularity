package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReadCandleRepository {
    Optional<Candle> getAt(String instrumentUid, Instant at);

    Optional<Candle> findClosestBefore(String instrumentUid, Instant at);

    List<Candle> getPeriod(String instrumentUid, Instant from, Instant to);

    List<Candle> findByOpenPrice(FindPriceParams params);
}
