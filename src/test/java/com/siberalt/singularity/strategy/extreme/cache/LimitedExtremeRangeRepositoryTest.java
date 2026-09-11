package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitedExtremeRangeRepositoryTest {
    private static final String INSTRUMENT = "instrument1";
    private static final String TYPE = "DEFAULT";

    private final ExtremeRangeRepository delegate = new RuntimeExtremeRangeRepository();
    private final ExtremeRepository extremes = new RuntimeExtremeRepository();

    @Test
    void keepsWhatFitsWithinTheBudget() {
        LimitedExtremeRangeRepository repository = limitedTo(20);

        cache(repository, outer(0, 9), inner(0, 9));
        cache(repository, outer(100, 109), inner(100, 109));

        assertEquals(List.of(outer(0, 9), outer(100, 109)), heldOuterRanges(repository));
        assertEquals(20, cachedExtremes().size());
    }

    @Test
    void dropsTheColdestRangeWhenTheBudgetIsPassed() {
        LimitedExtremeRangeRepository repository = limitedTo(20);

        cache(repository, outer(0, 9), inner(0, 9));
        cache(repository, outer(100, 109), inner(100, 109));
        cache(repository, outer(200, 209), inner(200, 209));

        assertEquals(List.of(outer(100, 109), outer(200, 209)), heldOuterRanges(repository));
        assertEquals(List.of(inner(100, 109), inner(200, 209)), heldInnerRanges(repository));
        assertTrue(cachedExtremes().stream().allMatch(candle -> candle.getIndex() >= 100));
    }

    /** Being asked about a range is what keeps it alive; the one nobody returns to goes first. */
    @Test
    void spendsTheBudgetOnWhatIsBeingAskedAbout() {
        LimitedExtremeRangeRepository repository = limitedTo(20);

        cache(repository, outer(0, 9), inner(0, 9));
        cache(repository, outer(100, 109), inner(100, 109));
        repository.getIntersects(outer(0, 9), RangeType.OUTER);
        cache(repository, outer(200, 209), inner(200, 209));

        assertEquals(List.of(outer(0, 9), outer(200, 209)), heldOuterRanges(repository));
    }

    /**
     * The case whole-range eviction cannot answer: the locator unites each window with what it
     * touches, so one range grows past the budget on its own and never goes cold.
     */
    @Test
    void cutsDownARangeThatOutgrowsTheBudgetByItself() {
        LimitedExtremeRangeRepository repository = limitedTo(8);

        cache(repository, outer(0, 19), inner(0, 19));

        assertEquals(List.of(outer(12, 19)), heldOuterRanges(repository));
        assertEquals(List.of(inner(12, 19)), heldInnerRanges(repository));
        assertEquals(8, cachedExtremes().size());
        assertTrue(cachedExtremes().stream().allMatch(candle -> candle.getIndex() >= 12));
    }

    @Test
    void cutsTowardsTheEndLastAskedAbout() {
        LimitedExtremeRangeRepository repository = limitedTo(8);

        repository.getIntersects(outer(0, 3), RangeType.OUTER);
        cache(repository, outer(0, 19), inner(0, 19));

        assertEquals(List.of(outer(0, 7)), heldOuterRanges(repository));
        assertEquals(List.of(inner(0, 7)), heldInnerRanges(repository));
        assertTrue(cachedExtremes().stream().allMatch(candle -> candle.getIndex() <= 7));
    }

    /**
     * An INNER range claims its stretch has been scanned. One left standing over extremes that were
     * evicted would have the locator skip work it no longer holds the answer to.
     */
    @Test
    void neverLeavesAScannedClaimOverEvictedExtremes() {
        LimitedExtremeRangeRepository repository = limitedTo(8);

        cache(repository, outer(0, 19), inner(4, 15));

        List<Candle> remaining = cachedExtremes();
        List<ExtremeRange> claims = heldInnerRanges(repository);

        assertEquals(List.of(inner(12, 15)), claims);
        assertTrue(remaining.stream().allMatch(candle -> candle.getIndex() >= 12));
    }

    @Test
    void budgetsEachInstrumentSeparately() {
        LimitedExtremeRangeRepository repository = limitedTo(20);

        cache(repository, outer(0, 9), inner(0, 9));
        cache(repository, outer(100, 109), inner(100, 109));
        repository.saveBatch(List.of(
            new ExtremeRange(0, 19, "instrument2", TYPE, RangeType.OUTER)
        ));

        assertEquals(List.of(outer(0, 9), outer(100, 109)), heldOuterRanges(repository));
    }

    /**
     * A budget counted in index units is a count of bars only for raw candles. Rolled-up ones carry
     * the index of the raw bar they opened on, so a window of them spans many times its own length
     * in units - and a flat budget then evicts the window the caller is working in, every call.
     */
    @Test
    void sizesTheBudgetByTheWindowWhenBarsSitFarApart() {
        LimitedExtremeRangeRepository repository =
            new LimitedExtremeRangeRepository(delegate, extremes, 8, 2);

        repository.getIntersects(outer(0, 99), RangeType.OUTER);
        cache(repository, outer(0, 99), inner(0, 99));

        assertEquals(List.of(outer(0, 99)), heldOuterRanges(repository));
        assertEquals(100, cachedExtremes().size());
    }

    /**
     * The locator replaces a range by deleting the old one before saving the new, so for a moment
     * nothing is held at all. The window-sized budget has to survive that moment: it is sized by what
     * the caller asks about, and the caller has not stopped asking.
     */
    @Test
    void keepsTheWindowBudgetWhileARangeIsReplaced() {
        LimitedExtremeRangeRepository repository =
            new LimitedExtremeRangeRepository(delegate, extremes, 8, 2);

        repository.getIntersects(outer(0, 99), RangeType.OUTER);
        cache(repository, outer(0, 99), inner(0, 99));

        repository.deleteBatch(List.of(outer(0, 99)));
        repository.saveBatch(List.of(outer(0, 109)));

        assertEquals(List.of(outer(0, 109)), heldOuterRanges(repository));
        assertEquals(100, cachedExtremes().size());
    }

    @Test
    void refusesToBeBuiltWithNothingToLimit() {
        assertThrows(IllegalArgumentException.class,
            () -> new LimitedExtremeRangeRepository(null, extremes));
        assertThrows(IllegalArgumentException.class,
            () -> new LimitedExtremeRangeRepository(delegate, null));
        assertThrows(IllegalArgumentException.class,
            () -> new LimitedExtremeRangeRepository(delegate, extremes, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new LimitedExtremeRangeRepository(delegate, extremes, 8, 0));
    }

    /** A flat budget, with the window-sized one turned down to where it cannot raise it. */
    private LimitedExtremeRangeRepository limitedTo(long maxCachedLength) {
        return new LimitedExtremeRangeRepository(delegate, extremes, maxCachedLength, 1);
    }

    /** Saves a scanned stretch the way the locator does: the ranges, and the extremes under them. */
    private void cache(LimitedExtremeRangeRepository repository, ExtremeRange outer, ExtremeRange inner) {
        extremes.saveBatch(inner, candlesOf(inner));
        repository.saveBatch(List.of(outer, inner));
    }

    private List<Candle> candlesOf(ExtremeRange range) {
        return java.util.stream.LongStream.rangeClosed(range.fromIndex(), range.toIndex())
            .mapToObj(this::candle)
            .toList();
    }

    private Candle candle(long index) {
        Quotation price = Quotation.of(100);

        return new Candle(INSTRUMENT, new TimePoint(index), price, price, price, price, 0);
    }

    private List<Candle> cachedExtremes() {
        return extremes.getByRange(outer(0, 1000));
    }

    private List<ExtremeRange> heldOuterRanges(LimitedExtremeRangeRepository repository) {
        return sorted(repository.getIntersects(outer(0, 1000), RangeType.OUTER));
    }

    private List<ExtremeRange> heldInnerRanges(LimitedExtremeRangeRepository repository) {
        return sorted(repository.getIntersects(outer(0, 1000), RangeType.INNER));
    }

    private List<ExtremeRange> sorted(List<ExtremeRange> ranges) {
        return ranges.stream()
            .sorted(java.util.Comparator.comparingLong(ExtremeRange::fromIndex))
            .toList();
    }

    private ExtremeRange outer(long fromIndex, long toIndex) {
        return new ExtremeRange(fromIndex, toIndex, INSTRUMENT, TYPE, RangeType.OUTER);
    }

    private ExtremeRange inner(long fromIndex, long toIndex) {
        return new ExtremeRange(fromIndex, toIndex, INSTRUMENT, TYPE, RangeType.INNER);
    }
}
