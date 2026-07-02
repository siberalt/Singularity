package com.siberalt.singularity.shared;

import com.siberalt.singularity.entity.candle.TimePoint;

import java.time.Instant;
import java.util.Objects;

public record TimePointRange(
    TimePoint fromPoint,
    TimePoint toPoint
) {
    public static TimePointRange EMPTY = new TimePointRange(TimePoint.NULL, TimePoint.NULL);

    public long fromIndex() {
        return fromPoint.index();
    }

    public long toIndex() {
        return toPoint.index();
    }

    public Instant fromTime() {
        return fromPoint.time();
    }

    public Instant toTime() {
        return toPoint.time();
    }

    public TimePointRange(Instant singleInstant) {
        this(new TimePoint(singleInstant));
    }

    public TimePointRange(TimePoint singlePoint) {
        this(singlePoint, singlePoint);
    }

    public boolean isEmpty() {
        return this == EMPTY || Objects.equals(fromPoint(), null) || Objects.equals(toPoint(),null);
    }

    public boolean isIndexRange() {
        return fromIndex() != -1 && toIndex() != -1;
    }

    public static TimePointRange union(TimePointRange range1, TimePointRange range2) {
        if (range1.isIndexRange() && range2.isIndexRange()) {
            return unionByIndexes(range1, range2);
        } else if (!range1.isIndexRange() && !range2.isIndexRange()) {
            return unionByInstants(range1, range2);
        } else {
            throw new IllegalArgumentException("Non-matching range types");
        }
    }
    
    public static TimePointRange unionByIndexes(TimePointRange range1, TimePointRange range2) {
        if (range1.isEmpty()) {
            return range2;
        } else if (range2.isEmpty()) {
            return range1;
        }

        TimePoint fromPoint = range1.fromIndex() < range2.fromIndex() ? range1.fromPoint() : range2.fromPoint();
        TimePoint toPoint = range1.toIndex() > range2.toIndex() ? range1.toPoint() : range2.toPoint();
        return new TimePointRange(fromPoint, toPoint);
    }

    public static TimePointRange unionByInstants(TimePointRange range1, TimePointRange range2) {
        if (range1.isEmpty()) {
            return range2;
        } else if (range2.isEmpty()) {
            return range1;
        }

        Instant fromTime1 = range1.fromTime();
        Instant fromTime2 = range2.fromTime();
        Instant toTime1 = range1.toTime();
        Instant toTime2 = range2.toTime();

        TimePoint fromPoint = fromTime1.isBefore(fromTime2) ? range1.fromPoint() : range2.fromPoint();
        TimePoint toPoint = toTime1.isAfter(toTime2) ? range1.toPoint() : range2.toPoint();
        return new TimePointRange(fromPoint, toPoint);
    }
}
