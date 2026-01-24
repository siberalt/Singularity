package com.siberalt.singularity.strategy.extreme.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RuntimeRangeRepository implements RangeRepository {
    private final List<Range> ranges = new ArrayList<>();

    @Override
    public List<Range> getIntersects(Range range, RangeType intersectType) {
        return ranges.stream()
            .filter(r -> r.isOverlapping(range) && r.rangeType() == intersectType)
            .collect(Collectors.toList());
    }

    @Override
    public List<Range> getSubsets(Range range, RangeType subsetType) {
        return ranges.stream()
            .filter(r -> r.isSubsetOf(range) && r.rangeType() == subsetType)
            .collect(Collectors.toList());
    }

    @Override
    public List<Range> getNeighbors(Range range, RangeType neighborType) {
        return ranges.stream()
            .filter(r -> r.rangeType() == neighborType &&
                r.instrumentId().equals(range.instrumentId()) &&
                r.extremeType().equals(range.extremeType()) &&
                (r.toIndex() + 1 == range.fromIndex() || r.fromIndex() - 1 == range.toIndex()))
            .collect(Collectors.toList());
    }

    @Override
    public void saveBatch(List<Range> rangesToSave) {
        ranges.addAll(rangesToSave);
    }

    @Override
    public void deleteBatch(List<Range> rangesToDelete) {
        ranges.removeAll(rangesToDelete);
    }
}
