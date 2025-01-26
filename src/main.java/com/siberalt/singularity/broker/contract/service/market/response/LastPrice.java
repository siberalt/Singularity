package com.siberalt.singularity.broker.contract.service.market.response;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class LastPrice {
    protected Quotation price;
    protected Instant time;
    protected String instrumentUid;

    public Quotation getPrice() {
        return price;
    }

    public LastPrice setPrice(Quotation price) {
        this.price = price;
        return this;
    }

    public Instant getTime() {
        return time;
    }

    public LastPrice setTime(Instant time) {
        this.time = time;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public LastPrice setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public static LastPrice of(String instrumentUid, Instant time, Quotation price) {
        return new LastPrice()
                .setPrice(price)
                .setInstrumentUid(instrumentUid)
                .setTime(time);
    }
}
