package com.siberalt.singularity.utils.entity;

public class NoOpCandleMigrationCheckpointRepository implements CandleMigrationCheckpointRepository {
    @Override
    public boolean isDone(String instrumentUid, MigrationChunk chunk) {
        return false;
    }

    @Override
    public void markDone(String instrumentUid, MigrationChunk chunk) {
        // Прогресс не сохраняется - используется, когда персистентный чекпойнт не нужен
        // (например, в тестах или для источников без необходимости в resumability).
    }
}
