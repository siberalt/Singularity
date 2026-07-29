package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.shared.TimePointRange;

public record CandleRangeMetadata(
    TimePointRange range,   // актуальный диапазон времени (может быть уже, чем запрошенный)
    long count              // количество свечей в этом диапазоне
) {
    public static final CandleRangeMetadata EMPTY = new CandleRangeMetadata(TimePointRange.EMPTY, 0);

    public boolean isEmpty() {
        return count == 0 || range.isEmpty();
    }
}
