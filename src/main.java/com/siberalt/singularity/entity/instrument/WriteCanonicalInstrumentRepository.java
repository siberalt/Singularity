package com.siberalt.singularity.entity.instrument;

public interface WriteCanonicalInstrumentRepository {
    CanonicalInstrument save(CanonicalInstrument instrument);

    void delete(CanonicalInstrument instrument);
}
