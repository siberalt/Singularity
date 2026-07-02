package com.siberalt.singularity.entity.position;

import java.util.Optional;

public interface ReadPositionRepository {
    Optional<Position> getByAccountAndInstrumentId(String accountId, String instrumentId);

    Optional<Position> get(String positionId);
}
