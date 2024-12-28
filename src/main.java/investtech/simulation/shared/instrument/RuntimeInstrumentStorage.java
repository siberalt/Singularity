package investtech.simulation.shared.instrument;

import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.instrument.common.InstrumentType;

import java.util.*;

public class RuntimeInstrumentStorage implements InstrumentStorageInterface {
    protected Map<String, Instrument> instruments = new HashMap<>();
    protected Map<InstrumentType, Set<Instrument>> instrumentsByType = new HashMap<>();

    public RuntimeInstrumentStorage add(Instrument instrument) {
        instruments.put(instrument.getUid(), instrument);
        instrumentsByType.computeIfAbsent(
                instrument.getInstrumentType(), key -> HashSet.newHashSet(20)
        ).add(instrument);

        return this;
    }

    @Override
    public Instrument get(String id) {
        return instruments.getOrDefault(id, null);
    }

    @Override
    public Iterable<Instrument> getByType(InstrumentType instrumentType) {
        return instrumentsByType.containsKey(instrumentType)
                ? instrumentsByType.get(instrumentType)
                : Collections::emptyIterator;
    }
}
