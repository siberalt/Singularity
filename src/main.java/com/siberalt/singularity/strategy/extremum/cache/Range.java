package com.siberalt.singularity.strategy.extremum.cache;

import java.util.ArrayList;
import java.util.List;

public record Range(
    String id,
    long fromIndex,
    long toIndex,
    String instrumentId,
    String extremeType,
    RangeType rangeType
) {
    Range(long fromIndex, long toIndex, String instrumentId, String extremumType, RangeType rangeType) {
        this(
            instrumentId + "-" + extremumType + "-" + fromIndex + "-" + toIndex,
            fromIndex,
            toIndex,
            instrumentId,
            extremumType,
            rangeType
        );
    }

    Range(long fromIndex, long toIndex, String instrumentId, String extremumType) {
        this(
            instrumentId + "-" + extremumType + "-" + fromIndex + "-" + toIndex,
            fromIndex,
            toIndex,
            instrumentId,
            extremumType,
            RangeType.OUTER
        );
    }

    public long length() {
        return toIndex - fromIndex + 1;
    }

    public boolean isEdgeSubsetOf(Range other) {
        if (!this.isSubsetOf(other)) {
            return false;
        }
        return this.fromIndex == other.fromIndex || this.toIndex == other.toIndex;
    }

    public boolean isOverlapping(Range other) {
        return this.instrumentId.equals(other.instrumentId) && this.extremeType.equals(other.extremeType)
            && this.fromIndex <= other.toIndex
            && this.toIndex >= other.fromIndex;
    }

    public boolean isSubsetOf(Range other) {
        return this.instrumentId.equals(other.instrumentId) && this.extremeType.equals(other.extremeType)
            && this.fromIndex >= other.fromIndex
            && this.toIndex <= other.toIndex;
    }

    public Range intersection(Range other) {
        if (!this.isOverlapping(other)) {
            return null; // No intersection
        }

        long intersectFromIndex = Math.max(this.fromIndex(), other.fromIndex());
        long intersectToIndex = Math.min(this.toIndex(), other.toIndex());

        return new Range(intersectFromIndex, intersectToIndex, this.instrumentId(), this.extremeType());
    }

    public Range subtract(Range intersectedRange) {
        if (!isOverlappingAndMatching(this, intersectedRange)) {
            return null; // No overlap, return null
        }

        long newFromIndex = this.fromIndex;
        long newToIndex = this.toIndex;

        if (intersectedRange.fromIndex > this.fromIndex) {
            newToIndex = intersectedRange.fromIndex - 1;
        } else if (intersectedRange.toIndex < this.toIndex) {
            newFromIndex = intersectedRange.toIndex + 1;
        } else {
            throw new IllegalArgumentException("Cannot subtract the entire range: " + intersectedRange);
        }

        return new Range(newFromIndex, newToIndex, this.instrumentId, this.extremeType);
    }

    public List<Range> subtract(List<Range> intersectedRanges) {
        if (intersectedRanges.isEmpty()) {
            return List.of(this);
        }

        List<Range> result = new ArrayList<>();
        long currentStart = this.fromIndex;

        // Filter intersectedRanges to include only those that overlap with the current range
        List<Range> validIntersectedRanges = intersectedRanges.stream()
            .filter(range -> isOverlappingAndMatching(this, range))
            .toList();

        for (Range intersected : validIntersectedRanges) {
            if (intersected.fromIndex > currentStart) {
                result.add(new Range(
                    currentStart,
                    intersected.fromIndex - 1,
                    this.instrumentId,
                    this.extremeType
                ));
            }
            currentStart = Math.max(currentStart, intersected.toIndex + 1);
        }

        if (currentStart <= this.toIndex) {
            result.add(new Range(
                currentStart,
                this.toIndex,
                this.instrumentId,
                this.extremeType
            ));
        }

        return result;
    }

    public static Range average(List<Range> ranges) {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Cannot average empty range list");
        }

        String instrumentId = ranges.get(0).instrumentId();
        String extremeType = ranges.get(0).extremeType();
        long fromIndex = Math.round(ranges.stream().mapToLong(Range::fromIndex).average().orElseThrow());
        long toIndex = Math.round(ranges.stream().mapToLong(Range::toIndex).average().orElseThrow());

        return new Range(fromIndex, toIndex, instrumentId, extremeType);
    }

    public static Range unite(List<Range> ranges) {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Cannot unite empty range list");
        }

        String instrumentId = ranges.get(0).instrumentId();
        String extremeType = ranges.get(0).extremeType();
        long fromIndex = ranges.stream().mapToLong(Range::fromIndex).min().orElseThrow();
        long toIndex = ranges.stream().mapToLong(Range::toIndex).max().orElseThrow();

        return new Range(fromIndex, toIndex, instrumentId, extremeType);
    }

    private boolean isOverlappingAndMatching(Range range, Range other) {
        return range.instrumentId.equals(other.instrumentId)
            && range.extremeType.equals(other.extremeType)
            && range.fromIndex <= other.toIndex
            && range.toIndex >= other.fromIndex;
    }
}
