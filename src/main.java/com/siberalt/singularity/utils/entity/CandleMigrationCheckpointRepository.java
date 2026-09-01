package com.siberalt.singularity.utils.entity;

public interface CandleMigrationCheckpointRepository {
    boolean isDone(String instrumentUid, MigrationChunk chunk);

    void markDone(String instrumentUid, MigrationChunk chunk);
}
