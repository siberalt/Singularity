package com.siberalt.singularity.broker.contract.service.instrument.response;

import com.siberalt.singularity.entity.instrument.Instrument;

public class GetResponse {
    protected Instrument instrument = null;

    public Instrument getInstrument() {
        return instrument;
    }

    public GetResponse setInstrument(Instrument instrument) {
        this.instrument = instrument;
        return this;
    }
}
