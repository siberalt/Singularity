package com.siberalt.singularity.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record RangeDouble(double fromIndex, double toIndex) {
    public RangeDouble {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex cannot be greater than toIndex");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RangeDouble that = (RangeDouble) o;
        return Double.compare(that.fromIndex, fromIndex) == 0 && Double.compare(that.toIndex, toIndex) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromIndex, toIndex);
    }

    public double length() {
        return toIndex - fromIndex + 1;
    }

    public boolean isOverlapping(RangeDouble other) {
        return this.fromIndex <= other.toIndex && this.toIndex >= other.fromIndex;
    }

    public boolean isSubsetOf(RangeDouble other) {
        return this.fromIndex >= other.fromIndex && this.toIndex <= other.toIndex;
    }

    public RangeDouble subtract(RangeDouble intersectedRange) {
        if (!this.isOverlapping(intersectedRange)) {
            return this; // No overlap, return the original range
        }

        double newFromIndex = this.fromIndex;
        double newToIndex = this.toIndex;

        if (intersectedRange.fromIndex > this.fromIndex) {
            newToIndex = Math.nextDown(intersectedRange.fromIndex); // Use Math.nextDown for precision-safe subtraction
        } else if (intersectedRange.toIndex < this.toIndex) {
            newFromIndex = Math.nextUp(intersectedRange.toIndex); // Use Math.nextUp for precision-safe addition
        } else {
            // The intersected range completely covers this range, resulting in an empty range
            return null;
        }

        if (newFromIndex > newToIndex) {
            throw new IllegalArgumentException("Invalid range after subtraction: fromIndex=" + newFromIndex + ", toIndex=" + newToIndex);
        }

        return new RangeDouble(newFromIndex, newToIndex);
    }

    public List<RangeDouble> subtract(List<RangeDouble> intersectedRanges) {
        if (intersectedRanges.isEmpty()) {
            return List.of(this);
        }

        List<RangeDouble> result = new ArrayList<>();
        double currentStart = this.fromIndex;

        // Filter intersectedRanges to include only those that overlap with the current range
        List<RangeDouble> validIntersectedRanges = intersectedRanges.stream()
            .filter(this::isOverlapping)
            .toList();

        for (RangeDouble intersected : validIntersectedRanges) {
            if (intersected.fromIndex > currentStart) {
                result.add(new RangeDouble(
                    currentStart,
                    intersected.fromIndex - 1
                ));
            }
            currentStart = Math.max(currentStart, intersected.toIndex + 1);
        }

        if (currentStart <= this.toIndex) {
            result.add(new RangeDouble(
                currentStart,
                this.toIndex
            ));
        }

        return result;
    }

    public RangeDouble intersection(RangeDouble other) {
        if (!this.isOverlapping(other)) {
            return null; // No intersection
        }

        double intersectFromIndex = Math.max(this.fromIndex(), other.fromIndex());
        double intersectToIndex = Math.min(this.toIndex(), other.toIndex());

        return new RangeDouble(intersectFromIndex, intersectToIndex);
    }
}
