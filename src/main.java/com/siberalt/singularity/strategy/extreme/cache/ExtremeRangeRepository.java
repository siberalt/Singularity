package com.siberalt.singularity.strategy.extreme.cache;

import java.util.List;

public interface ExtremeRangeRepository {
    List<ExtremeRange> getIntersects(ExtremeRange range, RangeType intersectType);
    List<ExtremeRange> getSubsets(ExtremeRange range, RangeType subsetType);
    List<ExtremeRange> getNeighbors(ExtremeRange range, RangeType neighborType);
    void saveBatch(List<ExtremeRange> range);
    void deleteBatch(List<ExtremeRange> ranges);
}
