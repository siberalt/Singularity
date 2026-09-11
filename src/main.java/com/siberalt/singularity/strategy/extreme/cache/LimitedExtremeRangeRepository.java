package com.siberalt.singularity.strategy.extreme.cache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps a cache of located extremes from growing without end: it passes every range through to the
 * repository it wraps, and once the ranges it holds for one instrument outgrow their budget it
 * throws the coldest of them away, taking the extremes underneath with them.
 * <p>
 * Deciding what to keep belongs here rather than in {@link CachingExtremeLocator}. The locator
 * knows which stretch of candles was asked for and which part of it is new - the two questions it
 * has to answer. How much of the past is worth paying for is not one of them, and it used to be
 * answered halfway: the locator trimmed the window it was about to save, but only ever the one
 * window it happened to be touching, so a second instrument, or a second stretch far from the
 * first, was held forever. Asked at the repository the question is answered for everything cached,
 * because the repository is what knows everything cached.
 * <p>
 * Two things are thrown away, in that order. Whole ranges go first, coldest by last use, because a
 * range nothing has asked about is the cheapest thing to lose. That alone is not enough: the
 * locator unites each new window with the ranges it touches, so walking forward through a year
 * leaves one range that grows without bound and never goes cold. So a range still over budget on
 * its own is cut down to it, keeping the end nearer whatever was last asked for.
 * <p>
 * Both leave the cache consistent rather than merely smaller. The extremes under a dropped stretch
 * are deleted, and INNER ranges are clipped to what survives - an INNER range claims its stretch
 * has been scanned, so one left standing over deleted extremes would make the locator skip work it
 * no longer has the answer to and quietly return fewer extremes than there are.
 * <p>
 * Budgets are per instrument and extreme type, so a busy instrument cannot evict a quiet one.
 * <p>
 * Not thread safe, like the repositories it wraps. It also assumes it is the only writer to its
 * delegate, since it counts what it holds rather than asking.
 */
public class LimitedExtremeRangeRepository implements ExtremeRangeRepository {
    /** A month of minute candles, the size the locator used to cap a single window at. */
    public static final long DEFAULT_MAX_CACHED_LENGTH = 43200;

    private final ExtremeRangeRepository rangeRepository;
    private final ExtremeRepository extremeRepository;
    private final long maxCachedLength;

    /** OUTER ranges held per instrument and extreme type, each against when it was last used. */
    private final Map<String, Map<ExtremeRange, Long>> heldRanges = new HashMap<>();

    /** The last stretch asked about per instrument and extreme type - where the cache is warm. */
    private final Map<String, ExtremeRange> lastAsked = new HashMap<>();

    private long clock;

    public LimitedExtremeRangeRepository(
        ExtremeRangeRepository rangeRepository,
        ExtremeRepository extremeRepository
    ) {
        this(rangeRepository, extremeRepository, DEFAULT_MAX_CACHED_LENGTH);
    }

    public LimitedExtremeRangeRepository(
        ExtremeRangeRepository rangeRepository,
        ExtremeRepository extremeRepository,
        long maxCachedLength
    ) {
        if (rangeRepository == null || extremeRepository == null) {
            throw new IllegalArgumentException("Nothing to limit");
        }

        if (maxCachedLength < 1) {
            throw new IllegalArgumentException("A cache has to hold something, got " + maxCachedLength);
        }

        this.rangeRepository = rangeRepository;
        this.extremeRepository = extremeRepository;
        this.maxCachedLength = maxCachedLength;
    }

    public long getMaxCachedLength() {
        return maxCachedLength;
    }

    @Override
    public List<ExtremeRange> getIntersects(ExtremeRange range, RangeType intersectType) {
        if (intersectType == RangeType.OUTER) {
            lastAsked.put(keyOf(range), range);
        }

        return touch(rangeRepository.getIntersects(range, intersectType));
    }

    @Override
    public List<ExtremeRange> getSubsets(ExtremeRange range, RangeType subsetType) {
        return touch(rangeRepository.getSubsets(range, subsetType));
    }

    @Override
    public List<ExtremeRange> getNeighbors(ExtremeRange range, RangeType neighborType) {
        return touch(rangeRepository.getNeighbors(range, neighborType));
    }

    @Override
    public void saveBatch(List<ExtremeRange> rangesToSave) {
        rangeRepository.saveBatch(rangesToSave);

        for (ExtremeRange range : rangesToSave) {
            if (range.rangeType() == RangeType.OUTER) {
                bucketOf(keyOf(range)).put(range, ++clock);
            }
        }

        for (ExtremeRange range : rangesToSave) {
            if (range.rangeType() == RangeType.OUTER) {
                evict(keyOf(range), rangesToSave);
            }
        }
    }

    @Override
    public void deleteBatch(List<ExtremeRange> rangesToDelete) {
        rangeRepository.deleteBatch(rangesToDelete);

        for (ExtremeRange range : rangesToDelete) {
            if (range.rangeType() == RangeType.OUTER) {
                forget(range);
            }
        }
    }

    /**
     * Marks whatever came back as used, so what a caller keeps returning to outlives what it has
     * stopped asking about.
     */
    private List<ExtremeRange> touch(List<ExtremeRange> found) {
        for (ExtremeRange range : found) {
            if (range.rangeType() != RangeType.OUTER) {
                continue;
            }

            Map<ExtremeRange, Long> bucket = heldRanges.get(keyOf(range));

            if (bucket != null && bucket.containsKey(range)) {
                bucket.put(range, ++clock);
            }
        }

        return found;
    }

    /**
     * Brings one instrument back within its budget. Ranges saved in the batch that triggered this
     * are spared - they are the reason the cache was just used, and evicting them would throw away
     * the work of the call in progress.
     */
    private void evict(String key, List<ExtremeRange> justSaved) {
        Map<ExtremeRange, Long> bucket = heldRanges.get(key);

        if (bucket == null) {
            return;
        }

        long cached = cachedLength(bucket);

        if (cached <= maxCachedLength) {
            return;
        }

        List<ExtremeRange> coldestFirst = bucket.keySet().stream()
            .filter(range -> !justSaved.contains(range))
            .sorted(Comparator.comparingLong(bucket::get))
            .toList();

        List<ExtremeRange> dropped = new ArrayList<>();

        for (int index = 0; cached > maxCachedLength && index < coldestFirst.size(); index++) {
            ExtremeRange cold = coldestFirst.get(index);

            purge(cold);
            dropped.add(cold);
            bucket.remove(cold);
            cached -= cold.length();
        }

        if (!dropped.isEmpty()) {
            rangeRepository.deleteBatch(dropped);
        }

        // Reachable, and the case that matters: with every cold range gone the bucket holds only
        // what this very batch saved, and one window grown past the budget on its own is exactly
        // what whole-range eviction cannot touch.
        if (cached > maxCachedLength) {
            trim(key, bucket, cached);
        }
    }

    /**
     * Cuts the longest range held down to what the budget leaves it, keeping the end nearer the
     * stretch last asked about. Nothing has been asked yet on the first save, and then the newest
     * end is kept: a window walking forward through history is the reason a range grows this far in
     * the first place.
     */
    private void trim(String key, Map<ExtremeRange, Long> bucket, long cached) {
        ExtremeRange longest = bucket.keySet().stream()
            .max(Comparator.comparingLong(ExtremeRange::length))
            .orElse(null);

        if (longest == null) {
            return;
        }

        long keepLength = longest.length() - (cached - maxCachedLength);

        if (keepLength < 1) {
            purge(longest);
            rangeRepository.deleteBatch(List.of(longest));
            bucket.remove(longest);

            return;
        }

        ExtremeRange kept = keptEndOf(longest, keepLength, lastAsked.get(key));

        for (ExtremeRange dropped : longest.subtract(List.of(kept), longest.rangeType())) {
            purge(dropped);
        }

        rangeRepository.deleteBatch(List.of(longest));
        rangeRepository.saveBatch(List.of(kept));
        bucket.remove(longest);
        bucket.put(kept, ++clock);
    }

    private ExtremeRange keptEndOf(ExtremeRange range, long keepLength, ExtremeRange asked) {
        boolean keepNewestEnd = asked == null
            || Math.abs(asked.toIndex() - range.toIndex()) <= Math.abs(asked.fromIndex() - range.fromIndex());

        if (keepNewestEnd) {
            return rangeOf(range, range.toIndex() - keepLength + 1, range.toIndex());
        }

        return rangeOf(range, range.fromIndex(), range.fromIndex() + keepLength - 1);
    }

    /**
     * Forgets everything under a stretch: the extremes located there, and the claim that it was
     * ever scanned. INNER ranges reaching past it keep the part that survives.
     */
    private void purge(ExtremeRange region) {
        List<ExtremeRange> staleInnerRanges = rangeRepository.getIntersects(region, RangeType.INNER);

        if (!staleInnerRanges.isEmpty()) {
            List<ExtremeRange> survivors = new ArrayList<>();

            for (ExtremeRange stale : staleInnerRanges) {
                survivors.addAll(stale.subtract(List.of(region), RangeType.INNER));
            }

            rangeRepository.deleteBatch(staleInnerRanges);

            if (!survivors.isEmpty()) {
                rangeRepository.saveBatch(survivors);
            }
        }

        extremeRepository.deleteBatch(List.of(region));
    }

    private long cachedLength(Map<ExtremeRange, Long> bucket) {
        return bucket.keySet().stream().mapToLong(ExtremeRange::length).sum();
    }

    private Map<ExtremeRange, Long> bucketOf(String key) {
        return heldRanges.computeIfAbsent(key, ignored -> new HashMap<>());
    }

    private void forget(ExtremeRange range) {
        String key = keyOf(range);
        Map<ExtremeRange, Long> bucket = heldRanges.get(key);

        if (bucket == null) {
            return;
        }

        bucket.remove(range);

        if (bucket.isEmpty()) {
            heldRanges.remove(key);
            lastAsked.remove(key);
        }
    }

    private ExtremeRange rangeOf(ExtremeRange range, long fromIndex, long toIndex) {
        return new ExtremeRange(fromIndex, toIndex, range.instrumentId(), range.extremeType(), range.rangeType());
    }

    private String keyOf(ExtremeRange range) {
        return range.instrumentId() + ":" + range.extremeType();
    }
}
