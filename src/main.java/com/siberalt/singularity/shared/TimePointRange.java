package com.siberalt.singularity.shared;

import com.siberalt.singularity.entity.candle.TimePoint;

import java.time.Instant;

public record TimePointRange(
    TimePoint fromPoint,
    TimePoint toPoint
) {
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

     public static TimePointRange union(TimePointRange range1, TimePointRange range2) {
         TimePoint fromPoint = range1.fromIndex() < range2.fromIndex() ? range1.fromPoint() : range2.fromPoint();
         TimePoint toPoint = range1.toIndex() > range2.toIndex() ? range1.toPoint() : range2.toPoint();
         return new TimePointRange(fromPoint, toPoint);
     }
}
