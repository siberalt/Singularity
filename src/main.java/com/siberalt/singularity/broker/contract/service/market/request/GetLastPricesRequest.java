package com.siberalt.singularity.broker.contract.service.market.request;

import java.time.Duration;
import java.util.List;

public class GetLastPricesRequest {
    protected List<String> instrumentsUid;
    protected Duration period;

    public List<String> getInstrumentsUid() {
        return instrumentsUid;
    }

    public GetLastPricesRequest setPeriod(Duration period) {
        this.period = period;
        return this;
    }

    public Duration getPeriod() {
        return period;
    }

    public GetLastPricesRequest setInstrumentsUid(List<String> instrumentsUid) {
        this.instrumentsUid = instrumentsUid;
        return this;
    }

    public GetLastPricesRequest setInstrumentUid(String instrumentUid) {
        this.instrumentsUid = List.of(instrumentUid);
        return this;
    }

    public static GetLastPricesRequest of(List<String> instrumentsUid) {
        return new GetLastPricesRequest().setInstrumentsUid(instrumentsUid);
    }

    public static GetLastPricesRequest of(String instrumentUid) {
        return of(List.of(instrumentUid));
    }
}
