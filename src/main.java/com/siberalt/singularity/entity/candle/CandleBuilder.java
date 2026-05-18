package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class CandleBuilder {
    private String instrumentUid;
    private Instant time;
    private Quotation open;
    private Quotation close;
    private Quotation high;
    private Quotation low;
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

    public CandleBuilder setOpen(Quotation open) {
        this.open = open;
        return this;
    }

    public CandleBuilder setClose(Quotation closePrice) {
        this.close = closePrice;
        return this;
    }

    public CandleBuilder setHigh(Quotation high) {
        this.high = high;
        return this;
    }

    public CandleBuilder setLow(Quotation low) {
        this.low = low;
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
        return new Candle(
            instrumentUid,
            new TimePoint(index, time),
            open,
            close,
            high,
            low,
            volume
        );
    }
}
