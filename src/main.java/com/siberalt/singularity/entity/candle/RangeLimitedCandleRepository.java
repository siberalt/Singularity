package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.shared.TimeRange;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class RangeLimitedCandleRepository implements ReadCandleRepository {
    private final ReadCandleRepository delegate;
    private final Supplier<TimeRange> timeRangeSupplier;

    public RangeLimitedCandleRepository(ReadCandleRepository delegate,  Supplier<TimeRange> timeRangeSupplier) {
        this.delegate = delegate;
        this.timeRangeSupplier = timeRangeSupplier;
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        TimeRange timeRange = timeRangeSupplier.get();
        Instant from = timeRange.from();
        Instant to = timeRange.to();

        if (at.isBefore(from) || at.isAfter(to)) {
            return Optional.empty();
        }
        return delegate.getAt(instrumentUid, at);
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        TimeRange timeRange = timeRangeSupplier.get();
        Instant from = timeRange.from();

        if (at.isBefore(from)) {
            return Collections.emptyList();
        }
        return delegate.findBeforeOrEqual(instrumentUid, at, amountBefore)
            .stream()
            .filter(candle -> !candle.getTime().isBefore(from))
            .toList();
    }

    @Override
    public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
        return List.of();
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        TimeRange timeRange = timeRangeSupplier.get();
        Instant rangeFrom = timeRange.from();
        Instant rangeTo = timeRange.to();

        Instant adjustedFrom = from.isBefore(rangeFrom) ? rangeFrom : from;
        Instant adjustedTo = to.isAfter(rangeTo) ? rangeTo : to;

        if (adjustedFrom.isAfter(adjustedTo)) {
            return List.of();
        }
        return delegate.getPeriod(instrumentUid, adjustedFrom, adjustedTo);
    }

    @Override
    public List<Candle> findByPrice(FindPriceParams params) {
        TimeRange timeRange = timeRangeSupplier.get();
        Instant rangeFrom = timeRange.from();
        Instant rangeTo = timeRange.to();

        if (params.from().isAfter(rangeTo) || params.to().isBefore(rangeFrom)) {
            return List.of();
        }

        Instant adjustedFrom = params.from().isBefore(rangeFrom) ? rangeFrom : params.from();
        Instant adjustedTo = params.to().isAfter(rangeTo) ? rangeTo : params.to();

        return delegate.findByPrice(params.withRange(adjustedFrom, adjustedTo));
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
        TimeRange timeRange = timeRangeSupplier.get();
        Instant rangeFrom = timeRange.from();
        Instant rangeTo = timeRange.to();

        Instant adjustedFrom = from.isBefore(rangeFrom) ? rangeFrom : from;
        Instant adjustedTo = to.isAfter(rangeTo) ? rangeTo : to;

        if (adjustedFrom.isAfter(adjustedTo)) {
            return CandleRangeMetadata.EMPTY;
        }

        return delegate.getRangeMetadata(instrumentUid, adjustedFrom, adjustedTo);
    }
}
