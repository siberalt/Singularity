package com.siberalt.singularity.broker.contract.service.market.response;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

public class GetCurrentPriceResponse {
    private Quotation price;
    private String instrumentUid;

    public Quotation getPrice() {
        return price;
    }

    public GetCurrentPriceResponse setPrice(Quotation price) {
        this.price = price;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public GetCurrentPriceResponse setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }
}
