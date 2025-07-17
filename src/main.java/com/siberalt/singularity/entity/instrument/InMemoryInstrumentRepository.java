package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

import java.util.*;

public class InMemoryInstrumentRepository implements InstrumentRepository {
    protected Map<String, Instrument> instruments = new HashMap<>();
    protected Map<InstrumentType, Set<Instrument>> instrumentsByType = new HashMap<>();

    public void save(Instrument instrument) {
        instruments.put(instrument.getUid(), instrument);
        instrumentsByType.computeIfAbsent(
                instrument.getInstrumentType(), key -> HashSet.newHashSet(20)
        ).add(instrument);
    }

    @Override
    public void delete(Instrument instrument) {
        instruments.remove(instrument.getUid());
        Set<Instrument> instrumentSet = instrumentsByType.get(instrument.getInstrumentType());
        if (instrumentSet != null) {
            instrumentSet.remove(instrument);
            if (instrumentSet.isEmpty()) {
                instrumentsByType.remove(instrument.getInstrumentType());
            }
        }
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

    @Override
    public List<Instrument> getAll() {
        return new ArrayList<>(instruments.values());
    }
}
