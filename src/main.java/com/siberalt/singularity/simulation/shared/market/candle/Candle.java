package com.siberalt.singularity.simulation.shared.market.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

public class Candle {
    String instrumentUid;

    Instant time;

    Quotation openPrice;

    Quotation closePrice;

    Quotation highPrice;

    Quotation lowPrice;

    long volume;

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public Candle setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public Instant getTime() {
        return time;
    }

    public Candle setTime(Instant time) {
        this.time = time;
        return this;
    }

    public Quotation getOpenPrice() {
        return openPrice;
    }

    public Candle setOpenPrice(Quotation openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    public Quotation getClosePrice() {
        return closePrice;
    }

    public Candle setClosePrice(Quotation closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    public Quotation getHighPrice() {
        return highPrice;
    }

    public Candle setHighPrice(Quotation highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    public Quotation getLowPrice() {
        return lowPrice;
    }

    public Candle setLowPrice(Quotation lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    public long getVolume() {
        return volume;
    }

    public Candle setVolume(long volume) {
        this.volume = volume;
        return this;
    }

    public Candle addCumulative(Candle candle) {
        this.lowPrice = Objects.requireNonNullElse(this.lowPrice, Quotation.ZERO).add(candle.lowPrice);
        this.highPrice = Objects.requireNonNullElse(this.highPrice, Quotation.ZERO).add(candle.highPrice);
        this.openPrice = Objects.requireNonNullElse(this.openPrice, Quotation.ZERO).add(candle.openPrice);
        this.closePrice = Objects.requireNonNullElse(this.closePrice, Quotation.ZERO).add(candle.closePrice);
        this.volume += candle.volume;

        return this;
    }

    @Override
    public Candle clone() {
        return new Candle()
                .setInstrumentUid(instrumentUid)
                .setTime(time)
                .setOpenPrice(openPrice)
                .setClosePrice(closePrice)
                .setHighPrice(highPrice)
                .setLowPrice(lowPrice)
                .setVolume(volume);
    }

    public Quotation getAveragePrice() {
        return getAveragePrice(RoundingMode.HALF_EVEN);
    }

    public Quotation getAveragePrice(RoundingMode roundingMode) {
        var sum = Stream.of(lowPrice, highPrice, closePrice, openPrice)
                .map(Objects::requireNonNull)
                .map(Quotation::toBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Quotation.of(sum.divide(BigDecimal.valueOf(4), roundingMode));
    }
}
