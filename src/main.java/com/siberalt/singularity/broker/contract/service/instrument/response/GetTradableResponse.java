package com.siberalt.singularity.broker.contract.service.instrument.response;

import com.siberalt.singularity.entity.instrument.Instrument;

import java.util.List;

public class GetTradableResponse {
    protected List<Instrument> instruments;

    public List<Instrument> getInstruments() {
        return instruments;
    }

    public GetTradableResponse setInstruments(List<Instrument> instruments) {
        this.instruments = instruments;
        return this;
    }
}
