package com.siberalt.singularity.entity.instrument;

import java.util.List;
import java.util.Optional;

public interface ReadCanonicalInstrumentRepository {
    Optional<CanonicalInstrument> get(long id);
    Optional<CanonicalInstrument> findByIsin(String isin);
    List<CanonicalInstrument> getAll();
}
