package com.siberalt.singularity.entity.candle;

public interface CandleRepository extends ReadCandleRepository, WriteCandleRepository {
    // This interface combines both read and write operations for candles.
    // It extends ReadCandleRepository and WriteCandleRepository to provide a complete API for candle management.
}
