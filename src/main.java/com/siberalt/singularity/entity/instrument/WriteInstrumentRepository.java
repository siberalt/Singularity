package com.siberalt.singularity.entity.instrument;

public interface WriteInstrumentRepository {
    void save(Instrument instrument);

    void delete(Instrument instrument);
}
