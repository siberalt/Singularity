package com.siberalt.singularity.entity.instrument;

public interface WriteInstrumentRepository {
    void save(String brokerId, Instrument instrument);

    void delete(String brokerId, Instrument instrument);
}
