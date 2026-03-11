package com.siberalt.singularity.shared;

import java.util.ArrayList;
import java.util.List;

public record RangeLong(long fromIndex, long toIndex) {
    public RangeLong {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex cannot be greater than toIndex");
        }
    }

    public long length() {
        return toIndex - fromIndex + 1;
    }

    public boolean isOverlapping(RangeLong other) {
        return this.fromIndex <= other.toIndex && this.toIndex >= other.fromIndex;
    }

    public boolean isSubsetOf(RangeLong other) {
        return this.fromIndex >= other.fromIndex && this.toIndex <= other.toIndex;
    }

    public boolean isEdgeSubsetOf(RangeLong other) {
        if (!this.isSubsetOf(other)) {
            return false;
        }
        return this.fromIndex == other.fromIndex || this.toIndex == other.toIndex;
    }

    public RangeLong subtract(RangeLong intersectedRange) {
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

        return new RangeLong(newFromIndex, newToIndex);
    }

    public List<RangeLong> subtract(List<RangeLong> intersectedRanges) {
        if (intersectedRanges.isEmpty()) {
            return List.of(this);
        }

        List<RangeLong> result = new ArrayList<>();
        long currentStart = this.fromIndex;

        // Filter intersectedRanges to include only those that overlap with the current range
        List<RangeLong> validIntersectedRanges = intersectedRanges.stream()
            .filter(this::isOverlapping)
            .toList();

        for (RangeLong intersected : validIntersectedRanges) {
            if (intersected.fromIndex > currentStart) {
                result.add(new RangeLong(
                    currentStart,
                    intersected.fromIndex - 1
                ));
            }
            currentStart = Math.max(currentStart, intersected.toIndex + 1);
        }

        if (currentStart <= this.toIndex) {
            result.add(new RangeLong(
                currentStart,
                this.toIndex
            ));
        }

        return result;
    }

    public static RangeLong average(List<RangeLong> ranges) {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Cannot average empty range list");
        }

        long fromIndex = Math.round(ranges.stream().mapToLong(RangeLong::fromIndex).average().orElseThrow());
        long toIndex = Math.round(ranges.stream().mapToLong(RangeLong::toIndex).average().orElseThrow());

        return new RangeLong(fromIndex, toIndex);
    }

    public RangeLong intersection(RangeLong other) {
        if (!this.isOverlapping(other)) {
            return null; // No intersection
        }

        long intersectFromIndex = Math.max(this.fromIndex(), other.fromIndex());
        long intersectToIndex = Math.min(this.toIndex(), other.toIndex());

        return new RangeLong(intersectFromIndex, intersectToIndex);
    }

    public static RangeLong unite(List<RangeLong> ranges) {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Cannot unite empty range list");
        }

        long fromIndex = ranges.stream().mapToLong(RangeLong::fromIndex).min().orElseThrow();
        long toIndex = ranges.stream().mapToLong(RangeLong::toIndex).max().orElseThrow();

        return new RangeLong(fromIndex, toIndex);
    }
}
