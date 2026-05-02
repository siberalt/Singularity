package com.siberalt.singularity.entity.candle;

import java.time.Instant;

public record TimePoint(long index, Instant time) {
    public static TimePoint NULL = new TimePoint(Candle.DEFAULT_INDEX, null);

    public TimePoint(long index) {
        this(index, null);
    }

    public TimePoint(Instant time) {
        this(Candle.DEFAULT_INDEX, time);
    }
}
