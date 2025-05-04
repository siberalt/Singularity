package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.Optional;

public interface ReadCandleRepository {
    Optional<Candle> getAt(String instrumentUid, Instant at);

    Iterable<Candle> getPeriod(String instrumentUid, Instant from, Instant to);

    Iterable<Candle> findByOpenPrice(FindPriceParams params);
}
