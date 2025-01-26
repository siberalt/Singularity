package com.siberalt.singularity.simulation.shared.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

public interface InstrumentStorageInterface {
    Instrument get(String id);

    Iterable<Instrument> getByType(InstrumentType instrumentType);
}
