package com.siberalt.singularity.entity.instrument;

public interface InstrumentRepository extends ReadInstrumentRepository, WriteInstrumentRepository {
    // This interface combines the functionality of both ReadInstrumentRepository and WriteInstrumentRepository
    // No additional methods are needed here, as it inherits all necessary methods from the two interfaces.
}
