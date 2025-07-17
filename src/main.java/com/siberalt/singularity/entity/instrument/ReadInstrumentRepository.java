package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

import java.util.List;

public interface ReadInstrumentRepository {
    Instrument get(String id);
    Iterable<Instrument> getByType(InstrumentType instrumentType);
    List<Instrument> getAll();
}
