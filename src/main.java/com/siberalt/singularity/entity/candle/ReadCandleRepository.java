package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReadCandleRepository extends MigrationCandleSource {
    Optional<Candle> getAt(String instrumentUid, Instant at);

    /**
     * The candle at {@code at} together with {@code amountBefore} before it, oldest first - so
     * {@code amountBefore + 1} of them where the history reaches that far back.
     * <p>
     * Worth stating because both halves of it have been read wrong: the count, and the order. The
     * last element is the one at {@code at}, and taking the first instead quietly prices against a
     * bar that has already gone.
     */
    List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore);

    List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter);

    /**
     * Candles matching the price condition the params describe, oldest first. Which of the candle's
     * prices is compared is the caller's choice - see {@link CandlePriceField}.
     */
    List<Candle> findByPrice(FindPriceParams params);
}
