package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PeriodLimitedCandleRepository implements ReadCandleRepository {
    private final ReadCandleRepository delegate;
    private final Instant from;
    private final Instant to;

    public PeriodLimitedCandleRepository(ReadCandleRepository delegate, Instant from, Instant to) {
        this.delegate = delegate;
        this.from = from;
        this.to = to;
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        if (at.isBefore(from) || at.isAfter(to)) {
            return Optional.empty();
        }
        return delegate.getAt(instrumentUid, at);
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        if (at.isBefore(from)) {
            return Collections.emptyList();
        }
        return delegate.findBeforeOrEqual(instrumentUid, at, amountBefore)
            .stream()
            .filter(candle -> !candle.getTime().isBefore(from))
            .toList();
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        Instant adjustedFrom = from.isBefore(this.from) ? this.from : from;
        Instant adjustedTo = to.isAfter(this.to) ? this.to : to;

        if (adjustedFrom.isAfter(adjustedTo)) {
            return List.of();
        }
        return delegate.getPeriod(instrumentUid, adjustedFrom, adjustedTo);
    }

    @Override
    public List<Candle> findByOpenPrice(FindPriceParams params) {
        if (params.from().isAfter(to) || params.to().isBefore(from)) {
            return List.of();
        }

        Instant adjustedFrom = params.from().isBefore(this.from) ? this.from : params.from();
        Instant adjustedTo = params.to().isAfter(this.to) ? this.to : params.to();

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
