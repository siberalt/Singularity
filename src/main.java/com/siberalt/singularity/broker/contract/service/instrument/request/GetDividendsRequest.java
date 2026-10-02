package com.siberalt.singularity.broker.contract.service.instrument.request;

import java.time.Instant;

public class GetDividendsRequest {
    protected String instrumentUid;
    protected Instant from;
    protected Instant to;

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public GetDividendsRequest setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public Instant getFrom() {
        return from;
    }

    public GetDividendsRequest setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public GetDividendsRequest setTo(Instant to) {
        this.to = to;
        return this;
    }

    public static GetDividendsRequest of(String instrumentUid, Instant from, Instant to) {
        return new GetDividendsRequest()
            .setInstrumentUid(instrumentUid)
            .setFrom(from)
            .setTo(to);
    }
}
