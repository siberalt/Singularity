package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReadCandleRepository extends MigrationCandleSource {
    Optional<Candle> getAt(String instrumentUid, Instant at);

    List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore);

    List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter);

    List<Candle> findByOpenPrice(FindPriceParams params);
}
