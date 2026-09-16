package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CurrentTimeLimitedCandleRepository implements ReadCandleRepository {
    private final ReadCandleRepository delegate;

    private final Clock clock;

    public CurrentTimeLimitedCandleRepository(ReadCandleRepository delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    public Optional<Candle> getAt(long instrumentId, Instant at) {
        if (at.isAfter(clock.currentTime())) {
            return Optional.empty();
        }
        return delegate.getAt(instrumentId, at);
    }

    @Override
    public List<Candle> findBeforeOrEqual(long instrumentId, Instant at, long amountBefore) {
        if (at.isAfter(clock.currentTime())) {
            return Collections.emptyList();
        }
        return delegate.findBeforeOrEqual(instrumentId, at, amountBefore);
    }

    @Override
    public List<Candle> findAfterOrEqual(long instrumentId, Instant at, long amountAfter) {
        if (at.isAfter(clock.currentTime())) {
            return Collections.emptyList();
        }
        return delegate.findAfterOrEqual(instrumentId, at, amountAfter);
    }

    @Override
    public List<Candle> getPeriod(long instrumentId, Instant from, Instant to) {
        Instant currentTime = clock.currentTime();

        if (from.isAfter(currentTime)) {
            return List.of();
        }
        Instant adjustedTo = to.isAfter(currentTime) ? currentTime : to;
        return delegate.getPeriod(instrumentId, from, adjustedTo);
    }

    @Override
    public List<Candle> findByPrice(long instrumentId, FindPriceParams params) {
        Instant currentTime = clock.currentTime();

        if (params.from().isAfter(currentTime)) {
            return List.of();
        }
        Instant adjustedFrom = params.from().isBefore(currentTime) ? params.from() : currentTime;
        Instant adjustedTo = params.to().isAfter(currentTime) ? currentTime : params.to();

        return delegate.findByPrice(instrumentId, params.withRange(adjustedFrom, adjustedTo));
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(long instrumentId, Instant from, Instant to) {
        Instant currentTime = clock.currentTime();

        if (from.isAfter(currentTime)) {
            return CandleRangeMetadata.EMPTY;
        }
        Instant adjustedTo = to.isAfter(currentTime) ? currentTime : to;
        return delegate.getRangeMetadata(instrumentId, from, adjustedTo);
    }
}
