package com.siberalt.singularity.entity.instrument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryCanonicalInstrumentRepository implements CanonicalInstrumentRepository {
    protected final Map<Long, CanonicalInstrument> instrumentsById = new HashMap<>();
    protected final Map<String, Long> idsByIsin = new HashMap<>();
    protected final AtomicLong nextId = new AtomicLong(1);

    @Override
    public CanonicalInstrument save(CanonicalInstrument instrument) {
        if (instrument.getId() == null) {
            instrument.setId(nextId.getAndIncrement());
        }

        instrumentsById.put(instrument.getId(), instrument);
        if (instrument.getIsin() != null) {
            idsByIsin.put(instrument.getIsin(), instrument.getId());
        }

        return instrument;
    }

    @Override
    public void delete(CanonicalInstrument instrument) {
        instrumentsById.remove(instrument.getId());
        if (instrument.getIsin() != null) {
            idsByIsin.remove(instrument.getIsin());
        }
    }

    @Override
    public Optional<CanonicalInstrument> get(long id) {
        return Optional.ofNullable(instrumentsById.get(id));
    }

    @Override
    public Optional<CanonicalInstrument> findByIsin(String isin) {
        Long id = idsByIsin.get(isin);
        return id == null ? Optional.empty() : Optional.ofNullable(instrumentsById.get(id));
    }

    @Override
    public List<CanonicalInstrument> getAll() {
        return List.copyOf(instrumentsById.values());
    }
}
