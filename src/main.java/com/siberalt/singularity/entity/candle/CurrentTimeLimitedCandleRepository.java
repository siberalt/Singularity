package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CurrentTimeLimitedCandleRepository implements ReadCandleRepository {
    private final ReadCandleRepository delegate;

    private final Instant currentTime;

    public CurrentTimeLimitedCandleRepository(ReadCandleRepository delegate, Instant currentTime) {
        this.delegate = delegate;
        this.currentTime = currentTime;
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        if (at.isAfter(currentTime)) {
            return Optional.empty();
        }
        return delegate.getAt(instrumentUid, at);
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        if (at.isAfter(currentTime)) {
            return Collections.emptyList();
        }
        return delegate.findBeforeOrEqual(instrumentUid, at, amountBefore);
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        if (from.isAfter(currentTime)) {
            return List.of();
        }
        Instant adjustedTo = to.isAfter(currentTime) ? currentTime : to;
        return delegate.getPeriod(instrumentUid, from, adjustedTo);
    }

    @Override
    public List<Candle> findByOpenPrice(FindPriceParams params) {
        if (params.from().isAfter(currentTime)) {
            return List.of();
        }
        Instant adjustedFrom = params.from().isBefore(currentTime) ? params.from() : currentTime;
        Instant adjustedTo = params.to().isAfter(currentTime) ? currentTime : params.to();

        FindPriceParams adjustedParams = new FindPriceParams(
            params.instrumentUid(),
            adjustedFrom,
            adjustedTo,
            params.price(),
            params.comparisonOperator(),
            params.maxCount()
        );

        return delegate.findByOpenPrice(adjustedParams);
    }
}
