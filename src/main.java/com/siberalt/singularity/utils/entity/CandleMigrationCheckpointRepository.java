package com.siberalt.singularity.utils.entity;

public interface CandleMigrationCheckpointRepository {
    boolean isDone(long instrumentId, MigrationChunk chunk);

    void markDone(long instrumentId, MigrationChunk chunk);
}
