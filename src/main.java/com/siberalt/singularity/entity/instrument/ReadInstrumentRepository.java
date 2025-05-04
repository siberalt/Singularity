package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

public interface ReadInstrumentRepository {
    Instrument get(String id);

    Iterable<Instrument> getByType(InstrumentType instrumentType);
}
