package com.siberalt.singularity.broker.contract.service.market.request;
import java.time.Instant;

public class GetCandlesRequest {
    protected Instant from;
    protected Instant to;
    protected CandleInterval interval;
    protected String instrumentUid;

    public Instant getFrom() {
        return from;
    }

    public GetCandlesRequest setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public GetCandlesRequest setTo(Instant to) {
        this.to = to;
        return this;
    }

    public CandleInterval getInterval() {
        return interval;
    }

    public GetCandlesRequest setInterval(CandleInterval interval) {
        this.interval = interval;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public GetCandlesRequest setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public static GetCandlesRequest of(Instant from, Instant to, CandleInterval interval, String instrumentUid) {
        return new GetCandlesRequest()
                .setFrom(from)
                .setTo(to)
                .setInstrumentUid(instrumentUid)
                .setInterval(interval);
    }
}
