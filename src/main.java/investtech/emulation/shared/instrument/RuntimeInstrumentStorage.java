package investtech.emulation.shared.instrument;

import com.google.common.collect.Iterables;
import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.instrument.common.InstrumentType;

import java.util.*;

public class RuntimeInstrumentStorage implements InstrumentStorageInterface {
    protected Map<String, Instrument> instruments = new HashMap<>();
    protected Map<InstrumentType, Set<Instrument>> instrumentsByType = new HashMap<>();

    public void add(Instrument instrument) {
        instruments.put(instrument.getUid(), instrument);
        instrumentsByType.computeIfAbsent(
                instrument.getInstrumentType(), key -> HashSet.newHashSet(20)
        ).add(instrument);
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
