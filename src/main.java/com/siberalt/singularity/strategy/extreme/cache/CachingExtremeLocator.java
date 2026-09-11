package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.RangeLong;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.utils.ListUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Locates extremes once and remembers where it has already looked, so a window that has mostly been
 * seen before costs only the part of it that is new.
 * <p>
 * Two kinds of range are kept. An OUTER range is a stretch of candles that has been scanned; an
 * INNER range is the stretch the extremes found there actually span. What has to be scanned on a
 * given call is the asked-for range less the INNER ranges already covering it.
 * <p>
 * Nothing here decides how much to keep. The cache grows as it is used, and it is the
 * {@link ExtremeRangeRepository} that puts a bound on it - see
 * {@link LimitedExtremeRangeRepository}, which this uses by default.
 * <p>
 * Not thread safe, and neither are the repositories it defaults to. Give each thread its own.
 */
public class CachingExtremeLocator implements ExtremeLocator {
    private record RangeExtremes(ExtremeRange extremeRange, List<Candle> extremes) {
    }

    private record CacheResult(ExtremeRange windowRange, List<RangeExtremes> rangeExtremes) {
    }

    public static String EXTREME_TYPE_DEFAULT = "DEFAULT";

    private final ExtremeLocator baseLocator;
    private final ExtremeRangeRepository rangeRepository;
    private final ExtremeRepository extremeRepository;
    private final String extremeType;

    public CachingExtremeLocator(ExtremeLocator baseLocator) {
        this(baseLocator, new RuntimeExtremeRepository());
    }

    private CachingExtremeLocator(ExtremeLocator baseLocator, ExtremeRepository extremeRepository) {
        this(
            baseLocator,
            new LimitedExtremeRangeRepository(new RuntimeExtremeRangeRepository(), extremeRepository),
            extremeRepository
        );
    }

    public CachingExtremeLocator(
        ExtremeLocator baseLocator,
        ExtremeRangeRepository rangeRepository,
        ExtremeRepository extremeRepository
    ) {
        this(baseLocator, rangeRepository, extremeRepository, EXTREME_TYPE_DEFAULT);
    }

    public CachingExtremeLocator(
        ExtremeLocator baseLocator,
        ExtremeRangeRepository rangeRepository,
        ExtremeRepository extremeRepository,
        String extremeType
    ) {
        this.baseLocator = baseLocator;
        this.rangeRepository = rangeRepository;
        this.extremeRepository = extremeRepository;
        this.extremeType = extremeType;
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
            List<Candle> extremes = cacheResult.rangeExtremes().getFirst().extremes();

            if (extremes.isEmpty()) {
                return List.of();
            }

            if (!outerNeighborsRanges.isEmpty()) {
                List<ExtremeRange> oldInnerRanges = rangeRepository.getSubsets(windowRange, RangeType.INNER);
                List<ExtremeRange> oldRanges = ListUtils.merge(outerNeighborsRanges, oldInnerRanges);
                updateRange(cacheResult.windowRange(), oldRanges);
                return extremes;
            }

            ExtremeRange innerRange = createRangeFromCandles(extremes, RangeType.INNER);
            addRange(cacheResult.windowRange(), innerRange);

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
        updateRange(cacheResult.windowRange(), ListUtils.merge(oldOuterRanges, oldInnerRanges));

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

        long fromIndex = candles.getFirst().getIndex();
        long toIndex = candles.getLast().getIndex();
        String instrumentId = candles.getFirst().instrumentUid();
        validateRange(fromIndex, toIndex);

        return new ExtremeRange(fromIndex, toIndex, instrumentId, extremeType, rangeType);
    }

    private CacheResult cacheRanges(
        List<ExtremeRange> rangesToCache,
        ExtremeRange windowRange,
        List<Candle> candles
    ) {
        List<RangeExtremes> locatedExtremes = new ArrayList<>();

        for (ExtremeRange rangeToCache : rangesToCache) {
            List<Candle> missingCandles = candlesOf(candles, rangeToCache.range());
            List<Candle> extremes = baseLocator.locate(missingCandles);

            locatedExtremes.add(new RangeExtremes(rangeToCache, extremes));

            if (!extremes.isEmpty()) {
                extremeRepository.saveBatch(rangeToCache, extremes);
            }
        }

        return new CacheResult(subtractEmptyEdgeRanges(locatedExtremes, windowRange), locatedExtremes);
    }

    /**
     * The candles a range covers, found through their indexes rather than by counting off from the
     * first one.
     * <p>
     * Counting off carried the same assumption that used to sit in the cluster aggregator: that
     * candles arrive one index apart, so a candle sits at its index less the first one. Raw candles
     * do arrive that way, the index being a row number. Candles rolled up to a wider interval do
     * not - an hourly bar keeps the index of the minute it opened on, so neighbours are some sixty
     * apart and the arithmetic ran off the end of the list.
     * <p>
     * A search over the indexes assumes nothing about the spacing. Unlike the average step the
     * cluster aggregator settles for, it also has to be exact here: these bounds decide which
     * candles the base locator is handed, not merely which neighbourhood something is measured over.
     */
    private List<Candle> candlesOf(List<Candle> candles, RangeLong range) {
        return candles.subList(
            lowerBound(candles, range.fromIndex()),
            lowerBound(candles, range.toIndex() + 1)
        );
    }

    /** Where the first candle whose index is not below {@code index} sits; the size if none is. */
    private int lowerBound(List<Candle> candles, long index) {
        int low = 0;
        int high = candles.size();

        while (low < high) {
            int middle = (low + high) >>> 1;

            if (candles.get(middle).getIndex() < index) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        return low;
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
            return trimmedRanges.getFirst();
        }

        throw new IllegalStateException("Invalid window extremeRange defragmentation: " + trimmedRanges);
    }

    private boolean isEmptyEdgeRange(RangeExtremes rangeExtremes, ExtremeRange windowRange) {
        return rangeExtremes.extremes().isEmpty() && rangeExtremes.extremeRange().isEdgeSubsetOf(windowRange);
    }

    private void addRange(ExtremeRange newOuterRange, ExtremeRange newInnerRange) {
        rangeRepository.saveBatch(List.of(newOuterRange, newInnerRange));
    }

    private void updateRange(ExtremeRange newOuterRange, List<ExtremeRange> oldRanges) {
        ExtremeRange newInnerRange = extremeRepository.getInnerRange(newOuterRange);

        if (null == newInnerRange) {
            rangeRepository.saveBatch(List.of(newOuterRange));
        } else {
            rangeRepository.saveBatch(List.of(newOuterRange, newInnerRange));
        }

        rangeRepository.deleteBatch(oldRanges);
    }

    private void validateRange(long fromIndex, long toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > toIndex) {
            throw new IllegalArgumentException("Invalid extremeRange indexes: fromIndex=" + fromIndex + ", toIndex=" + toIndex);
        }
    }
}
