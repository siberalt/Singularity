package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.utils.ListUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CachingExtremeLocator implements ExtremeLocator {
    private record RangeExtremes(Range range, List<Candle> extremes) {
    }

    private record CacheResult(Range updatedWindowRange, List<RangeExtremes> rangeExtremes) {
    }

    public static long MAX_RANGE_LENGTH = 43200; // Maximum range size for one month (1 candle = 1 minute)
    public static String EXTREME_TYPE_DEFAULT = "DEFAULT";

    private final ExtremeLocator baseLocator;
    private RangeRepository rangeRepository = new RuntimeRangeRepository();
    private ExtremeRepository extremeRepository = new RuntimeExtremeRepository();
    private String extremeType = EXTREME_TYPE_DEFAULT;
    private long maxRangeLength = MAX_RANGE_LENGTH;

    public CachingExtremeLocator(ExtremeLocator baseLocator) {
        this.baseLocator = baseLocator;
    }

    public CachingExtremeLocator(
        ExtremeLocator baseLocator,
        RangeRepository rangeRepository,
        ExtremeRepository extremeRepository
    ) {
        this.baseLocator = baseLocator;
        this.rangeRepository = rangeRepository;
        this.extremeRepository = extremeRepository;
    }

    public CachingExtremeLocator(
        ExtremeLocator baseLocator,
        RangeRepository rangeRepository,
        ExtremeRepository extremeRepository,
        String extremeType,
        long maxRangeLength
    ) {
        this.baseLocator = baseLocator;
        this.rangeRepository = rangeRepository;
        this.extremeRepository = extremeRepository;
        this.extremeType = extremeType;
        this.maxRangeLength = maxRangeLength;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        if (candles.isEmpty()) {
            return List.of();
        }

        Range outerRange = createRangeFromCandles(candles, RangeType.OUTER);
        List<Range> outerIntersectedRanges = rangeRepository.getIntersects(outerRange, RangeType.OUTER);

        if (outerIntersectedRanges.isEmpty()) {
            List<Range> outerNeighborsRanges = rangeRepository.getNeighbors(outerRange, RangeType.OUTER);

            Range windowRange = Range.unite(ListUtils.merge(outerNeighborsRanges, List.of(outerRange)));
            CacheResult cacheResult = cacheRanges(List.of(outerRange), windowRange, candles);
            List<Candle> extremes = cacheResult.rangeExtremes().get(0).extremes();

            if (extremes.isEmpty()) {
                return List.of();
            }

            if (!outerNeighborsRanges.isEmpty()) {
                List<Range> oldInnerRanges = rangeRepository.getSubsets(windowRange, RangeType.INNER);
                List<Range> oldRanges = ListUtils.merge(outerNeighborsRanges, oldInnerRanges);
                updateRange(cacheResult.updatedWindowRange(), oldRanges);
                return extremes;
            }

            Range innerRange = createRangeFromCandles(extremes, RangeType.INNER);
            addRange(cacheResult.updatedWindowRange(), innerRange);

            return extremes;
        }

        for (Range outerIntersectedRange : outerIntersectedRanges) {
            if (outerRange.isSubsetOf(outerIntersectedRange)) {
                return extremeRepository.getByRange(outerRange);
            }
        }

        List<Range> intersectedInnerRanges = rangeRepository.getIntersects(outerRange, RangeType.INNER);
        List<Range> missingInnerRanges = outerRange.subtract(intersectedInnerRanges);

        List<Range> outerNeighborsRanges = rangeRepository.getNeighbors(outerRange, RangeType.OUTER);
        List<Range> oldOuterRanges = ListUtils.merge(outerIntersectedRanges, outerNeighborsRanges);
        Range windowRange = Range.unite(ListUtils.merge(List.of(outerRange), oldOuterRanges));

        CacheResult cacheResult = cacheRanges(missingInnerRanges, windowRange, candles);

        List<Range> oldInnerRanges = rangeRepository.getSubsets(windowRange, RangeType.INNER);
        updateRange(cacheResult.updatedWindowRange(), ListUtils.merge(oldOuterRanges, oldInnerRanges));

        List<RangeExtremes> intersectedInnerExtremes = intersectedInnerRanges.stream()
            .map(range -> new RangeExtremes(range, extremeRepository.getByRange(range)))
            .toList();

        return ListUtils.merge(cacheResult.rangeExtremes(), intersectedInnerExtremes)
            .stream()
            .sorted(Comparator.comparingLong(re -> re.range().fromIndex()))
            .flatMap(re -> re.extremes().stream())
            .toList();
    }

    private Range createRangeFromCandles(
        List<Candle> candles,
        RangeType rangeType
    ) {
        if (candles.isEmpty()) {
            throw new IllegalArgumentException("Cannot create range from empty candle list");
        }

        long fromIndex = candles.get(0).getIndex();
        long toIndex = candles.get(candles.size() - 1).getIndex();
        String instrumentId = candles.get(0).getInstrumentUid();
        validateRange(fromIndex, toIndex);

        return new Range(fromIndex, toIndex, instrumentId, extremeType, rangeType);
    }

    private List<Range> detectOldRangesToDelete(List<Range> rangesToDelete, List<Range> missingRanges) {
        List<Range> oldRangesToDelete = new ArrayList<>();

        for (Range rangeToDelete : rangesToDelete) {
            missingRanges.stream()
                .map(rangeToDelete::subtract)
                .filter(Objects::nonNull)
                .forEach(oldRangesToDelete::add);
        }

        return oldRangesToDelete;
    }

    private CacheResult cacheRanges(
        List<Range> rangesToCache,
        Range windowRange,
        List<Candle> candles
    ) {
        List<RangeExtremes> locatedExtremes = new ArrayList<>();
        long startIndex = candles.get(0).getIndex();

        for (Range rangeToCache : rangesToCache) {
            List<Candle> missingCandles = candles.subList(
                (int) (rangeToCache.fromIndex() - startIndex),
                (int) (rangeToCache.toIndex() - startIndex + 1)
            );
            List<Candle> extremes = baseLocator.locate(missingCandles);

            locatedExtremes.add(new RangeExtremes(rangeToCache, extremes));
        }

        windowRange = subtractEmptyEdgeRanges(locatedExtremes, windowRange);

        Range adjustToRange = Range.average(rangesToCache);
        Range adjustedWindowRange = adjustRange(windowRange, adjustToRange, maxRangeLength);
        List<Range> rangesToDelete = windowRange.subtract(List.of(adjustedWindowRange));
        List<Range> oldRangesToDelete = detectOldRangesToDelete(rangesToDelete, rangesToCache);

        if (!oldRangesToDelete.isEmpty()) {
            extremeRepository.deleteBatch(oldRangesToDelete);
        }

        for (RangeExtremes rangeExtremes : locatedExtremes) {
            List<Candle> extremes = rangeExtremes.extremes();
            Range rangeToCache = rangeExtremes.range();

            if (!extremes.isEmpty()) {
                Range deleteRange = detectDeleteRange(rangeExtremes.range(), rangesToDelete);

                if (null != deleteRange) {
                    Range partialCacheRange = rangeExtremes.range().subtract(deleteRange);
                    List<Candle> candlesToCache = extractRangeCandles(
                        partialCacheRange,
                        rangeExtremes.extremes()
                    );
                    extremeRepository.saveBatch(partialCacheRange, candlesToCache);
                } else {
                    extremeRepository.saveBatch(rangeToCache, extremes);
                }
            }
        }

        return new CacheResult(adjustedWindowRange, locatedExtremes);
    }

    private Range subtractEmptyEdgeRanges(List<RangeExtremes> allExtremes, Range windowRange) {
        List<Range> emptyEdgeRanges = allExtremes
            .stream()
            .filter(re -> isEmptyEdgeRange(re, windowRange))
            .map(RangeExtremes::range)
            .toList();

        List<Range> trimmedRanges = windowRange.subtract(emptyEdgeRanges);

        if (trimmedRanges.isEmpty()) {
            return windowRange;
        }

        if (trimmedRanges.size() == 1) {
            return trimmedRanges.get(0);
        }

        throw new IllegalStateException("Invalid window range defragmentation: " + trimmedRanges);
    }

    private boolean isEmptyEdgeRange(RangeExtremes rangeExtremes, Range windowRange) {
        return rangeExtremes.extremes().isEmpty() && rangeExtremes.range().isEdgeSubsetOf(windowRange);
    }

    private List<Candle> extractRangeCandles(
        Range range,
        List<Candle> candles
    ) {
        long startIndex = -1;
        long endIndex = -1;

        for (int i = 0; i < candles.size(); i++) {
            long candleIndex = candles.get(i).getIndex();

            if (startIndex == -1 && candleIndex >= range.fromIndex()) {
                startIndex = i;
            } else if (candleIndex >= range.toIndex()) {
                endIndex = i - 1;
                break;
            }
        }

        endIndex = endIndex == -1 ? candles.size() - 1 : endIndex;

        if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
            throw new IllegalArgumentException("Cannot extract candles for range: " + range);
        }

        return candles.subList((int) startIndex, (int) endIndex + 1);
    }

    private Range detectDeleteRange(Range sourceRange, List<Range> rangesToDelete) {
        for (Range rangeToDelete : rangesToDelete) {
            Range intersectedRange = sourceRange.intersection(rangeToDelete);

            if (intersectedRange != null) {
                return intersectedRange;
            }
        }

        return null;
    }

    private void addRange(Range newOuterRange, Range newInnerRange) {
        rangeRepository.saveBatch(List.of(newOuterRange, newInnerRange));
    }

    private void updateRange(Range newOuterRange, List<Range> oldRanges) {
        Range newInnerRange = extremeRepository.getInnerRange(newOuterRange);
        rangeRepository.saveBatch(List.of(newOuterRange, newInnerRange));
        rangeRepository.deleteBatch(oldRanges);
    }

    private Range createOuterRange(long fromIndex, long toIndex, String instrumentId) {
        validateRange(fromIndex, toIndex);

        return new Range(fromIndex, toIndex, instrumentId, extremeType, RangeType.OUTER);
    }

    private void validateRange(long fromIndex, long toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > toIndex) {
            throw new IllegalArgumentException("Invalid range indexes: fromIndex=" + fromIndex + ", toIndex=" + toIndex);
        }
    }

    private Range adjustRange(Range unitedRange, Range outerRange, long maxRangeLength) {
        if (unitedRange.length() <= maxRangeLength) {
            return unitedRange;
        }

        long distanceToNewFromIndex = Math.abs(outerRange.fromIndex() - unitedRange.fromIndex());
        long distanceToNewToIndex = Math.abs(outerRange.toIndex() - unitedRange.toIndex());

        if (distanceToNewFromIndex < distanceToNewToIndex) {
            unitedRange = createOuterRange(
                outerRange.fromIndex(),
                outerRange.fromIndex() + maxRangeLength - 1,
                unitedRange.instrumentId()
            );
        } else {
            unitedRange = createOuterRange(
                outerRange.toIndex() - maxRangeLength + 1,
                outerRange.toIndex(),
                unitedRange.instrumentId()
            );
        }

        return unitedRange;
    }
}
