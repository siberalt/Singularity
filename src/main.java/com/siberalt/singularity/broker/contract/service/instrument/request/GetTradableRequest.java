package com.siberalt.singularity.broker.contract.service.instrument.request;

public class GetTradableRequest {
    protected String currency;

    public String getCurrency() {
        return currency;
    }

    public GetTradableRequest setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public static GetTradableRequest of(String currency) {
        return new GetTradableRequest().setCurrency(currency);
    }
}
