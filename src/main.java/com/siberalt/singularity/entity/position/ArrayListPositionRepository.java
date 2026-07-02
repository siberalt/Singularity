package com.siberalt.singularity.entity.position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArrayListPositionRepository implements PositionRepository {
    private final List<Position> positions = new ArrayList<>();

    @Override
    public Optional<Position> getByAccountAndInstrumentId(String accountId, String instrumentId) {
        return positions.stream()
                .filter(p -> accountId.equals(p.getPositionUid()) && instrumentId.equals(p.getInstrumentUid()))
                .findFirst();
    }

    @Override
    public Optional<Position> get(String positionId) {
        return positions.stream()
            .filter(p -> positionId.equals(p.getPositionUid()))
            .findFirst();
    }

    @Override
    public void save(Position position) {
        get(position.getPositionUid()).ifPresent(positions::remove);
        positions.add(position);
    }

    @Override
    public void remove(String positionId) {
        positions.removeIf(p -> p.getPositionUid().equals(positionId));
    }
}
