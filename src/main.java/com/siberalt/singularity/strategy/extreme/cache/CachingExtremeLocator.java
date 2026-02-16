package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.Range;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.utils.ListUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class CachingExtremeLocator implements ExtremeLocator {
    private record RangeExtremes(ExtremeRange extremeRange, List<Candle> extremes) {
    }

    private record CacheResult(ExtremeRange updatedWindowRange, List<RangeExtremes> rangeExtremes) {
    }

    public static long MAX_RANGE_LENGTH = 43200; // Maximum range size for one month (1 candle = 1 minute)
    public static String EXTREME_TYPE_DEFAULT = "DEFAULT";

    private final ExtremeLocator baseLocator;
    private ExtremeRangeRepository rangeRepository = new RuntimeExtremeRangeRepository();
    private ExtremeRepository extremeRepository = new RuntimeExtremeRepository();
    private String extremeType = EXTREME_TYPE_DEFAULT;
    private long maxRangeLength = MAX_RANGE_LENGTH;

    public CachingExtremeLocator(ExtremeLocator baseLocator) {
        this.baseLocator = baseLocator;
    }

    public CachingExtremeLocator(
        ExtremeLocator baseLocator,
        ExtremeRangeRepository rangeRepository,
        ExtremeRepository extremeRepository
    ) {
        this.baseLocator = baseLocator;
        this.rangeRepository = rangeRepository;
        this.extremeRepository = extremeRepository;
    }

    public CachingExtremeLocator(
        ExtremeLocator baseLocator,
        ExtremeRangeRepository rangeRepository,
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

        ExtremeRange outerRange = createRangeFromCandles(candles, RangeType.OUTER);
        List<ExtremeRange> outerIntersectedRanges = rangeRepository.getIntersects(outerRange, RangeType.OUTER);

        if (outerIntersectedRanges.isEmpty()) {
            List<ExtremeRange> outerNeighborsRanges = rangeRepository.getNeighbors(outerRange, RangeType.OUTER);

            ExtremeRange windowRange = ExtremeRange.unite(ListUtils.merge(outerNeighborsRanges, List.of(outerRange)));
            CacheResult cacheResult = cacheRanges(List.of(outerRange), windowRange, candles);
            List<Candle> extremes = cacheResult.rangeExtremes().get(0).extremes();

            if (extremes.isEmpty()) {
                return List.of();
            }

            if (!outerNeighborsRanges.isEmpty()) {
                List<ExtremeRange> oldInnerRanges = rangeRepository.getSubsets(windowRange, RangeType.INNER);
                List<ExtremeRange> oldRanges = ListUtils.merge(outerNeighborsRanges, oldInnerRanges);
                updateRange(cacheResult.updatedWindowRange(), oldRanges);
                return extremes;
            }

            ExtremeRange innerRange = createRangeFromCandles(extremes, RangeType.INNER);
            addRange(cacheResult.updatedWindowRange(), innerRange);

            return extremes;
        }

        for (ExtremeRange outerIntersectedRange : outerIntersectedRanges) {
            if (outerRange.isSubsetOf(outerIntersectedRange)) {
                return extremeRepository.getByRange(outerRange);
            }
        }

        List<ExtremeRange> intersectedInnerRanges = rangeRepository.getIntersects(outerRange, RangeType.INNER);
        List<ExtremeRange> missingInnerRanges = outerRange.subtract(intersectedInnerRanges, RangeType.INNER);

        List<ExtremeRange> outerNeighborsRanges = rangeRepository.getNeighbors(outerRange, RangeType.OUTER);
        List<ExtremeRange> oldOuterRanges = ListUtils.merge(outerIntersectedRanges, outerNeighborsRanges);
        ExtremeRange windowRange = ExtremeRange.unite(ListUtils.merge(List.of(outerRange), oldOuterRanges));

        CacheResult cacheResult = cacheRanges(missingInnerRanges, windowRange, candles);

        List<ExtremeRange> oldInnerRanges = rangeRepository.getSubsets(windowRange, RangeType.INNER);
        updateRange(cacheResult.updatedWindowRange(), ListUtils.merge(oldOuterRanges, oldInnerRanges));

        List<RangeExtremes> intersectedInnerExtremes = intersectedInnerRanges.stream()
            .map(range -> new RangeExtremes(range, extremeRepository.getByRange(range)))
            .toList();

        return ListUtils.merge(cacheResult.rangeExtremes(), intersectedInnerExtremes)
            .stream()
            .sorted(Comparator.comparingLong(re -> re.extremeRange().range().fromIndex()))
            .flatMap(re -> re.extremes().stream())
            .toList();
    }

    private ExtremeRange createRangeFromCandles(
        List<Candle> candles,
        RangeType rangeType
    ) {
        if (candles.isEmpty()) {
            throw new IllegalArgumentException("Cannot create extremeRange from empty candle list");
        }

        long fromIndex = candles.get(0).getIndex();
        long toIndex = candles.get(candles.size() - 1).getIndex();
        String instrumentId = candles.get(0).getInstrumentUid();
        validateRange(fromIndex, toIndex);

        return new ExtremeRange(fromIndex, toIndex, instrumentId, extremeType, rangeType);
    }

    private List<ExtremeRange> detectOldRangesToDelete(List<ExtremeRange> rangesToDelete, List<ExtremeRange> missingRanges) {
        List<ExtremeRange> oldRangesToDelete = new ArrayList<>();

        for (ExtremeRange rangeToDelete : rangesToDelete) {
            missingRanges.stream()
                .map(range -> rangeToDelete.subtract(List.of(range)))
                .filter(Objects::nonNull)
                .forEach(oldRangesToDelete::addAll);
        }

        return oldRangesToDelete;
    }

    private CacheResult cacheRanges(
        List<ExtremeRange> rangesToCache,
        ExtremeRange windowRange,
        List<Candle> candles
    ) {
        List<RangeExtremes> locatedExtremes = new ArrayList<>();
        long startIndex = candles.get(0).getIndex();

        for (ExtremeRange rangeToCache : rangesToCache) {
            Range range = rangeToCache.range();
            List<Candle> missingCandles = candles.subList(
                (int) (range.fromIndex() - startIndex),
                (int) (range.toIndex() - startIndex + 1)
            );
            List<Candle> extremes = baseLocator.locate(missingCandles);

            locatedExtremes.add(new RangeExtremes(rangeToCache, extremes));
        }

        windowRange = subtractEmptyEdgeRanges(locatedExtremes, windowRange);

        ExtremeRange adjustToRange = ExtremeRange.average(rangesToCache);
        ExtremeRange adjustedWindowRange = adjustRange(windowRange, adjustToRange, maxRangeLength);
        List<ExtremeRange> rangesToDelete = windowRange.subtract(List.of(adjustedWindowRange));
        List<ExtremeRange> oldRangesToDelete = detectOldRangesToDelete(rangesToDelete, rangesToCache);

        if (!oldRangesToDelete.isEmpty()) {
            extremeRepository.deleteBatch(oldRangesToDelete);
        }

        for (RangeExtremes rangeExtremes : locatedExtremes) {
            List<Candle> extremes = rangeExtremes.extremes();
            ExtremeRange rangeToCache = rangeExtremes.extremeRange();

            if (!extremes.isEmpty()) {
                ExtremeRange deleteRange = detectDeleteRange(rangeExtremes.extremeRange(), rangesToDelete);

                if (deleteRange != null) {
                    ExtremeRange partialCacheRange = rangeExtremes.extremeRange().subtract(deleteRange);

                    if (partialCacheRange != null) {
                        List<Candle> candlesToCache = extractRangeCandles(partialCacheRange, rangeExtremes.extremes());

                        if (!candlesToCache.isEmpty()) {
                            extremeRepository.saveBatch(partialCacheRange, candlesToCache);
                        }
                    }
                } else {
                    extremeRepository.saveBatch(rangeToCache, extremes);
                }
            }
        }

        return new CacheResult(adjustedWindowRange, locatedExtremes);
    }

    private ExtremeRange subtractEmptyEdgeRanges(List<RangeExtremes> allExtremes, ExtremeRange windowRange) {
        List<ExtremeRange> emptyEdgeRanges = allExtremes
            .stream()
            .filter(re -> isEmptyEdgeRange(re, windowRange))
            .map(RangeExtremes::extremeRange)
            .toList();

        List<ExtremeRange> trimmedRanges = windowRange.subtract(emptyEdgeRanges);

        if (trimmedRanges.isEmpty()) {
            return windowRange;
        }

        if (trimmedRanges.size() == 1) {
            return trimmedRanges.get(0);
        }

        throw new IllegalStateException("Invalid window extremeRange defragmentation: " + trimmedRanges);
    }

    private boolean isEmptyEdgeRange(RangeExtremes rangeExtremes, ExtremeRange windowRange) {
        return rangeExtremes.extremes().isEmpty() && rangeExtremes.extremeRange().isEdgeSubsetOf(windowRange);
    }

    private List<Candle> extractRangeCandles(
        ExtremeRange range,
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
            throw new IllegalArgumentException("Cannot extract candles for extremeRange: " + range);
        }

        return candles.subList((int) startIndex, (int) endIndex + 1);
    }

    private ExtremeRange detectDeleteRange(ExtremeRange sourceRange, List<ExtremeRange> rangesToDelete) {
        for (ExtremeRange rangeToDelete : rangesToDelete) {
            ExtremeRange intersectedRange = sourceRange.intersection(rangeToDelete);

            if (intersectedRange != null) {
                return intersectedRange;
            }
        }

        return null;
    }

    private void addRange(ExtremeRange newOuterRange, ExtremeRange newInnerRange) {
        rangeRepository.saveBatch(List.of(newOuterRange, newInnerRange));
    }

    private void updateRange(ExtremeRange newOuterRange, List<ExtremeRange> oldRanges) {
        ExtremeRange newInnerRange = extremeRepository.getInnerRange(newOuterRange);
        rangeRepository.saveBatch(List.of(newOuterRange, newInnerRange));
        rangeRepository.deleteBatch(oldRanges);
    }

    private ExtremeRange createOuterRange(long fromIndex, long toIndex, String instrumentId) {
        validateRange(fromIndex, toIndex);

        return new ExtremeRange(fromIndex, toIndex, instrumentId, extremeType, RangeType.OUTER);
    }

    private void validateRange(long fromIndex, long toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > toIndex) {
            throw new IllegalArgumentException("Invalid extremeRange indexes: fromIndex=" + fromIndex + ", toIndex=" + toIndex);
        }
    }

    private ExtremeRange adjustRange(ExtremeRange unitedRange, ExtremeRange outerRange, long maxRangeLength) {
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
