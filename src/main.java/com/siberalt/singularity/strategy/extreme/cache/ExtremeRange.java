package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.shared.Range;

import java.util.List;

public record ExtremeRange(
    String id,
    Range range,
    String instrumentId,
    String extremeType,
    RangeType rangeType
) {

    ExtremeRange(Range range, String instrumentId, String extremeType, RangeType rangeType) {
        this(
            generateId(instrumentId, extremeType, range.fromIndex(), range.toIndex()),
            range,
            instrumentId,
            extremeType,
            rangeType
        );
    }

    ExtremeRange(String id, long fromIndex, long toIndex, String instrumentId, String extremeType, RangeType rangeType) {
        this(
            id,
            new Range(fromIndex, toIndex),
            instrumentId,
            extremeType,
            rangeType
        );
    }

    ExtremeRange(long fromIndex, long toIndex, String instrumentId, String extremeType, RangeType rangeType) {
        this(
            generateId(instrumentId, extremeType, fromIndex, toIndex),
            fromIndex,
            toIndex,
            instrumentId,
            extremeType,
            rangeType
        );
    }

    ExtremeRange(long fromIndex, long toIndex, String instrumentId, String extremeType) {
        this(
            generateId(instrumentId, extremeType, fromIndex, toIndex),
            fromIndex,
            toIndex,
            instrumentId,
            extremeType,
            RangeType.OUTER
        );
    }

    public long fromIndex() {
        return range.fromIndex();
    }

    public long toIndex() {
        return range.toIndex();
    }

    public long length() {
        return range.length();
    }

    public boolean isEdgeSubsetOf(ExtremeRange other) {
        return range().isEdgeSubsetOf(other.range()) &&
            this.instrumentId.equals(other.instrumentId) &&
            this.extremeType.equals(other.extremeType);
    }

    public boolean isOverlapping(ExtremeRange other) {
        return this.instrumentId.equals(other.instrumentId) &&
            this.extremeType.equals(other.extremeType) &&
            this.range().isOverlapping(other.range());
    }

    public boolean isSubsetOf(ExtremeRange other) {
        return this.instrumentId.equals(other.instrumentId) && this.extremeType.equals(other.extremeType)
            && this.range().isSubsetOf(other.range());
    }

    public ExtremeRange intersection(ExtremeRange other) {
        return intersection(other, this.rangeType);
    }

    public ExtremeRange intersection(ExtremeRange other, RangeType resultRangeType) {
        if (!this.isOverlapping(other)) {
            return null; // No intersection
        }

        return new ExtremeRange(
            this.range().intersection(other.range()),
            this.instrumentId(),
            this.extremeType(),
            resultRangeType
        );
    }

    public ExtremeRange subtract(ExtremeRange intersectedRange) {
        return subtract(intersectedRange, this.rangeType);
    }

    public ExtremeRange subtract(ExtremeRange intersectedRange, RangeType resultRangeType) {
        if (!isHomogeneous(this, intersectedRange)) {
            return this; // Cannot subtract non-homogeneous ranges, return the original range
        }

        Range newRange = this.range().subtract(intersectedRange.range());

        if (newRange == null) {
            return null; // No valid range after subtraction
        }

        return new ExtremeRange(newRange, this.instrumentId, this.extremeType, resultRangeType);
    }

    public List<ExtremeRange> subtract(List<ExtremeRange> intersectedRanges) {
        return subtract(intersectedRanges, this.rangeType);
    }

    public List<ExtremeRange> subtract(List<ExtremeRange> intersectedRanges, RangeType resultRangeType) {
        if (intersectedRanges.isEmpty()) {
            return List.of(this);
        }

        // Filter intersectedRanges to include only those that overlap with the current range
        List<ExtremeRange> validIntersectedRanges = intersectedRanges.stream()
            .filter(range -> isHomogeneous(this, range))
            .toList();

        if (validIntersectedRanges.isEmpty()) {
            return List.of(this);
        }

        return this.range()
            .subtract(validIntersectedRanges.stream().map(ExtremeRange::range).toList()).stream()
            .map(r -> new ExtremeRange(r, this.instrumentId, this.extremeType, resultRangeType))
            .toList();
    }

    public static ExtremeRange average(List<ExtremeRange> ranges) {
        return average(ranges, ranges.get(0).rangeType());
    }

    public static ExtremeRange average(List<ExtremeRange> ranges, RangeType resultRangeType) {
        Range averageRange = Range.average(ranges.stream().map(ExtremeRange::range).toList());

        String instrumentId = ranges.get(0).instrumentId();
        String extremeType = ranges.get(0).extremeType();

        return new ExtremeRange(averageRange, instrumentId, extremeType, resultRangeType);
    }

    public static ExtremeRange unite(List<ExtremeRange> extremeRanges) {
        return unite(extremeRanges, extremeRanges.get(0).rangeType());
    }

    public static ExtremeRange unite(List<ExtremeRange> extremeRanges, RangeType resultRangeType) {
        Range unitedRange = Range.unite(extremeRanges.stream().map(ExtremeRange::range).toList());

        String instrumentId = extremeRanges.get(0).instrumentId();
        String extremeType = extremeRanges.get(0).extremeType();

        return new ExtremeRange(unitedRange, instrumentId, extremeType, resultRangeType);
    }

    private boolean isHomogeneous(ExtremeRange range, ExtremeRange other) {
        return range.instrumentId.equals(other.instrumentId)
            && range.extremeType.equals(other.extremeType);
    }

    private static String generateId(String instrumentId, String extremeType, long fromIndex, long toIndex) {
        return instrumentId + "-" + extremeType + "-" + fromIndex + "-" + toIndex;
    }
}
