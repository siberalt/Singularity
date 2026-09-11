package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.RangeLong;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.utils.ListUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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

        // Read before anything is saved. Saving can make a limited repository evict, and the answer to
        // this window must not depend on what storing it happened to push out.
        //
        // Clipped to the window asked about. An INNER range reaches as far back as the extremes found
        // under it, which on a slide is behind where this window starts, and handing all of it back
        // returned extremes from before the window.
        List<RangeExtremes> intersectedInnerExtremes = intersectedInnerRanges.stream()
            .map(range -> new RangeExtremes(range, extremeRepository.getByRange(range.intersection(outerRange))))
            .toList();

        CacheResult cacheResult = cacheRanges(missingInnerRanges, windowRange, candles);

        List<ExtremeRange> oldInnerRanges = rangeRepository.getSubsets(windowRange, RangeType.INNER);
        updateRange(cacheResult.windowRange(), ListUtils.merge(oldOuterRanges, oldInnerRanges));

        // Concatenated, not merged. The stretches just scanned are what was asked for less what was
        // cached, so no stretch is ever on both lists and there is nothing to deduplicate - and
        // deduplicating was not free: it hashed every stretch, which hashed every candle in it, and a
        // candle hashes by formatting its time and prices into strings. On a wide window that was
        // half of what a cached call cost.
        return Stream.concat(cacheResult.rangeExtremes().stream(), intersectedInnerExtremes.stream())
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
            List<Candle> extremes = baseLocator.locate(candlesOf(candles, rangeToCache.range()));

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
        List<ExtremeRange> newRanges = null == newInnerRange
            ? List.of(newOuterRange)
            : List.of(newOuterRange, newInnerRange);

        // Only what actually changed. A range that survives a call unchanged - most INNER ranges on a
        // slide, where no new extreme turned up - used to be saved as new and then deleted as old in
        // the same breath, and the delete took the copy just saved. The next window found no INNER
        // range, counted itself missing from end to end and was scanned whole: every other window.
        List<ExtremeRange> added = newRanges.stream().filter(range -> !oldRanges.contains(range)).toList();
        List<ExtremeRange> replaced = oldRanges.stream().filter(range -> !newRanges.contains(range)).toList();

        // The old go before the new arrive, never after. A repository that limits what it holds
        // would otherwise see both at once - the new window and the one it grew from, covering the
        // same bars twice - count them as twice the size, and evict the old one as cold, purging the
        // extremes underneath it that the new one still covers. On a slide that is most of them.
        if (!replaced.isEmpty()) {
            rangeRepository.deleteBatch(replaced);
        }

        if (!added.isEmpty()) {
            rangeRepository.saveBatch(added);
        }
    }

    private void validateRange(long fromIndex, long toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > toIndex) {
            throw new IllegalArgumentException("Invalid extremeRange indexes: fromIndex=" + fromIndex + ", toIndex=" + toIndex);
        }
    }
}
