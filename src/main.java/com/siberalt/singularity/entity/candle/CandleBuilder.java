package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class CandleBuilder {
    private String instrumentUid;
    private Instant time;
    private Quotation openPrice;
    private Quotation closePrice;
    private Quotation highPrice;
    private Quotation lowPrice;
    private long volume;
    private long index = Candle.DEFAULT_INDEX;

    public CandleBuilder setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public CandleBuilder setTime(Instant time) {
        this.time = time;
        return this;
    }

    public CandleBuilder setOpenPrice(Quotation openPrice) {
        this.openPrice = openPrice;
        return this;
    }

    public CandleBuilder setClosePrice(Quotation closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    public CandleBuilder setHighPrice(Quotation highPrice) {
        this.highPrice = highPrice;
        return this;
    }

    public CandleBuilder setLowPrice(Quotation lowPrice) {
        this.lowPrice = lowPrice;
        return this;
    }

    public CandleBuilder setVolume(long volume) {
        this.volume = volume;
        return this;
    }

    public CandleBuilder setIndex(long index) {
        this.index = index;
        return this;
    }

    public Candle build() {
        return new Candle()
            .setInstrumentUid(instrumentUid)
            .setTime(time)
            .setOpenPrice(openPrice)
            .setClosePrice(closePrice)
            .setHighPrice(highPrice)
            .setLowPrice(lowPrice)
            .setVolume(volume)
            .setIndex(index);
    }
}
