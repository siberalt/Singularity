package com.siberalt.singularity.shared;

import java.util.ArrayList;
import java.util.List;

public record Range(long fromIndex, long toIndex) {
    public long length() {
        return toIndex - fromIndex + 1;
    }

    public long getFromIndex() {
        return fromIndex;
    }

    public long getToIndex() {
        return toIndex;
    }

    public boolean isOverlapping(Range other) {
        return this.fromIndex <= other.toIndex && this.toIndex >= other.fromIndex;
    }

    public boolean isSubsetOf(Range other) {
        return this.fromIndex >= other.fromIndex && this.toIndex <= other.toIndex;
    }

    public boolean isEdgeSubsetOf(Range other) {
        if (!this.isSubsetOf(other)) {
            return false;
        }
        return this.fromIndex == other.fromIndex || this.toIndex == other.toIndex;
    }

    public Range subtract(Range intersectedRange) {
        if (!this.isOverlapping(intersectedRange)) {
            return this; // No overlap, return the original range
        }

        long newFromIndex = this.fromIndex;
        long newToIndex = this.toIndex;

        if (intersectedRange.fromIndex > this.fromIndex) {
            newToIndex = intersectedRange.fromIndex - 1;
        } else if (intersectedRange.toIndex < this.toIndex) {
            newFromIndex = intersectedRange.toIndex + 1;
        } else {
            // The intersected range completely covers this range, resulting in an empty range
            return null;
        }

        if (newFromIndex > newToIndex) {
            throw new IllegalArgumentException("Invalid range after subtraction: fromIndex=" + newFromIndex + ", toIndex=" + newToIndex);
        }

        return new Range(newFromIndex, newToIndex);
    }

    public List<Range> subtract(List<Range> intersectedRanges) {
        if (intersectedRanges.isEmpty()) {
            return List.of(this);
        }

        List<Range> result = new ArrayList<>();
        long currentStart = this.fromIndex;

        // Filter intersectedRanges to include only those that overlap with the current range
        List<Range> validIntersectedRanges = intersectedRanges.stream()
            .filter(this::isOverlapping)
            .toList();

        for (Range intersected : validIntersectedRanges) {
            if (intersected.fromIndex > currentStart) {
                result.add(new Range(
                    currentStart,
                    intersected.fromIndex - 1
                ));
            }
            currentStart = Math.max(currentStart, intersected.toIndex + 1);
        }

        if (currentStart <= this.toIndex) {
            result.add(new Range(
                currentStart,
                this.toIndex
            ));
        }

        return result;
    }

    public static Range average(List<Range> ranges) {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Cannot average empty range list");
        }

        long fromIndex = Math.round(ranges.stream().mapToLong(Range::fromIndex).average().orElseThrow());
        long toIndex = Math.round(ranges.stream().mapToLong(Range::toIndex).average().orElseThrow());

        return new Range(fromIndex, toIndex);
    }

    public Range intersection(Range other) {
        if (!this.isOverlapping(other)) {
            return null; // No intersection
        }

        long intersectFromIndex = Math.max(this.fromIndex(), other.fromIndex());
        long intersectToIndex = Math.min(this.toIndex(), other.toIndex());

        return new Range(intersectFromIndex, intersectToIndex);
    }

    public static Range unite(List<Range> ranges) {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Cannot unite empty range list");
        }

        long fromIndex = ranges.stream().mapToLong(Range::fromIndex).min().orElseThrow();
        long toIndex = ranges.stream().mapToLong(Range::toIndex).max().orElseThrow();

        return new Range(fromIndex, toIndex);
    }
}
