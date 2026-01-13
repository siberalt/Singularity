package com.siberalt.singularity.strategy.extremum.cache;

import java.util.List;

public interface RangeRepository {
    List<Range> getIntersects(Range range, RangeType intersectType);
    List<Range> getSubsets(Range range, RangeType subsetType);
    List<Range> getNeighbors(Range range, RangeType neighborType);
    void saveBatch(List<Range> range);
    void deleteBatch(List<Range> ranges);
}
