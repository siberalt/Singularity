package com.siberalt.singularity.entity.position;

public interface WritePositionRepository {
    void save(Position position);

    void remove(String positionId);
}
