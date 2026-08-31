package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

import java.util.List;
import java.util.Optional;

public interface ReadInstrumentRepository {
    Optional<Instrument> get(String brokerId, String id);
    Iterable<Instrument> getByType(String brokerId, InstrumentType instrumentType);
    List<Instrument> getAll(String brokerId);
}
