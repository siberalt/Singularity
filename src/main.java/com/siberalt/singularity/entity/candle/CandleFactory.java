package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;
import java.util.HashMap;

public class CandleFactory {
    String instrumentUid;
    long startIndex = 0;
    HashMap<Instant, Candle> candleCache = new HashMap<>();

    public CandleFactory(String instrumentUid, long startIndex) {
        this.instrumentUid = instrumentUid;
        this.startIndex = startIndex;
    }

    public CandleFactory(String instrumentUid) {
        this.instrumentUid = instrumentUid;
    }

    public Candle createCommon(Instant time, long volume, double commonValue) {
        return create(
            time,
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            volume
        );
    }

    public Candle createCommon(Instant time, double commonValue) {
        return create(
            time,
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            0
        );
    }

    public Candle createCommon(String timeAsString, long volume, double commonValue) {
        return create(
            Instant.parse(timeAsString),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            volume
        );
    }

    public Candle createCommon(String timeAsString, double commonValue) {
        return create(
            Instant.parse(timeAsString),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            Quotation.of(commonValue),
            0
        );
    }

    public Candle create(
        Instant time,
        Quotation openPrice,
        Quotation closePrice,
        Quotation highPrice,
        Quotation lowPrice,
        long volume
    ) {
        Candle candle = candleCache.get(time);

        if (candle != null) {
            if (isCandleDiverged(candle, openPrice, closePrice, highPrice, lowPrice, volume)) {
                throw new IllegalStateException(
                    "Candle with time " + time + " already exists in cache with different values."
                );
            }

            return candle;
        }

        candle = new Candle(
            instrumentUid,
            time,
            openPrice,
            closePrice,
            highPrice,
            lowPrice,
            volume,
            startIndex++
        );

        candleCache.put(time, candle);
        return candle;
    }

    private boolean isCandleDiverged(
        Candle cachedCandle,
        Quotation openPrice,
        Quotation closePrice,
        Quotation highPrice,
        Quotation lowPrice,
        long volume
    ) {
        return !cachedCandle.getOpenPrice().equals(openPrice) ||
            !cachedCandle.getClosePrice().equals(closePrice) ||
            !cachedCandle.getHighPrice().equals(highPrice) ||
            !cachedCandle.getLowPrice().equals(lowPrice) ||
            cachedCandle.getVolume() != volume;
    }
}
