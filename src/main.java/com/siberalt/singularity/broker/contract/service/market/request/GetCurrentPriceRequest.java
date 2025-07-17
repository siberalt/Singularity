package com.siberalt.singularity.broker.contract.service.market.request;

public class GetCurrentPriceRequest {
    private String instrumentUid;

    public GetCurrentPriceRequest(String instrumentUid) {
        this.instrumentUid = instrumentUid;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public GetCurrentPriceRequest setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }
}
