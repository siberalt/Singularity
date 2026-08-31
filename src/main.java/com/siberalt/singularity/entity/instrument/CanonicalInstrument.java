package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

public class CanonicalInstrument {
    protected Long id;
    protected String isin;
    protected String name;
    protected InstrumentType instrumentType;

    public Long getId() {
        return id;
    }

    public CanonicalInstrument setId(Long id) {
        this.id = id;
        return this;
    }

    public String getIsin() {
        return isin;
    }

    public CanonicalInstrument setIsin(String isin) {
        this.isin = isin;
        return this;
    }

    public String getName() {
        return name;
    }

    public CanonicalInstrument setName(String name) {
        this.name = name;
        return this;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public CanonicalInstrument setInstrumentType(InstrumentType instrumentType) {
        this.instrumentType = instrumentType;
        return this;
    }
}
