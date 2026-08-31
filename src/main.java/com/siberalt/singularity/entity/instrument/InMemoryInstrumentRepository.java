package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;

import java.util.*;

public class InMemoryInstrumentRepository implements InstrumentRepository {
    protected Map<String, Map<String, Instrument>> instrumentsByBroker = new HashMap<>();
    protected Map<String, Map<InstrumentType, Set<Instrument>>> instrumentsByBrokerAndType = new HashMap<>();

    public void save(String brokerId, Instrument instrument) {
        instrumentsByBroker.computeIfAbsent(brokerId, key -> new HashMap<>())
                .put(instrument.getUid(), instrument);
        instrumentsByBrokerAndType.computeIfAbsent(brokerId, key -> new HashMap<>())
                .computeIfAbsent(instrument.getInstrumentType(), key -> HashSet.newHashSet(20))
                .add(instrument);
    }

    @Override
    public void delete(String brokerId, Instrument instrument) {
        Map<String, Instrument> instruments = instrumentsByBroker.get(brokerId);
        if (instruments != null) {
            instruments.remove(instrument.getUid());
        }

        Map<InstrumentType, Set<Instrument>> byType = instrumentsByBrokerAndType.get(brokerId);
        if (byType != null) {
            Set<Instrument> instrumentSet = byType.get(instrument.getInstrumentType());
            if (instrumentSet != null) {
                instrumentSet.remove(instrument);
                if (instrumentSet.isEmpty()) {
                    byType.remove(instrument.getInstrumentType());
                }
            }
        }
    }

    @Override
    public Optional<Instrument> get(String brokerId, String id) {
        Map<String, Instrument> instruments = instrumentsByBroker.get(brokerId);
        return instruments == null ? Optional.empty() : Optional.ofNullable(instruments.get(id));
    }

    @Override
    public Iterable<Instrument> getByType(String brokerId, InstrumentType instrumentType) {
        Map<InstrumentType, Set<Instrument>> byType = instrumentsByBrokerAndType.get(brokerId);
        if (byType == null || !byType.containsKey(instrumentType)) {
            return Collections.emptySet();
        }
        return Set.copyOf(byType.get(instrumentType));
    }

    @Override
    public List<Instrument> getAll(String brokerId) {
        Map<String, Instrument> instruments = instrumentsByBroker.get(brokerId);
        return instruments == null ? Collections.emptyList() : List.copyOf(instruments.values());
    }
}
