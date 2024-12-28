package investtech.simulation.shared.instrument;

import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.instrument.common.InstrumentType;

public interface InstrumentStorageInterface {
    Instrument get(String id);

    Iterable<Instrument> getByType(InstrumentType instrumentType);
}
