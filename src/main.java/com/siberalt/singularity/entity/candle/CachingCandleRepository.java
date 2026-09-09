package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Remembers the candles a query returned, so the same stretch of history is decoded once however
 * many times it is asked for.
 * <p>
 * Reading candles is cheap; turning them into objects is not. A walk-forward run scores every
 * candidate over the same training stretch and then the winner over the same test stretch, so one
 * ten-fold run over nine candidates rebuilds the same hundred thousand candles a hundred times over.
 * The database was never the cost - a full read of four hundred thousand candles takes under a
 * third of a second - but the objects it produces are, both to build and for the collector to
 * clear afterwards.
 * <p>
 * Only results worth remembering are kept. A great many queries here ask for a single candle - what
 * is this order priced against, when does the market next open - and there is nothing to save in
 * recalling one; remembering them all would fill the cache with entries that cost more to hold than
 * to redo.
 * <p>
 * Thread-safe as far as its own bookkeeping goes, and no further: the delegate is called without a
 * lock, so it has to stand being called from several threads at once. Give it a repository over a
 * single database connection and that connection is what breaks. The reason the lock is not simply
 * widened to cover the delegate is that it was tried and it cost more than the cache saved - most
 * calls here ask for a single candle and are not worth remembering, so they miss every time, and
 * under a shared lock they turn a parallel run into a queue.
 * <p>
 * Two threads missing the same query at once both read it, and one of the two answers is kept. That
 * is a little wasted work in exchange for never holding a lock across a read, which is the right
 * way round when the read is the slow part.
 */
public class CachingCandleRepository implements ReadCandleRepository {
    /**
     * Roughly a gigabyte of candles. Large enough to hold every distinct stretch a walk-forward
     * asks for, and bounded because a cache that grows until the heap runs out is not a cache.
     */
    public static final int DEFAULT_MAX_CACHED_CANDLES = 2_000_000;

    /** Results shorter than this are not worth an entry, and are read afresh each time. */
    public static final int DEFAULT_MIN_CACHED_RESULT = 2;

    private enum Call {
        BEFORE_OR_EQUAL,
        AFTER_OR_EQUAL,
        PERIOD
    }

    private record Query(Call call, String instrumentUid, Instant from, Instant to, long amount) {
    }

    private final ReadCandleRepository base;
    private final Object lock = new Object();

    /** Access-ordered, so what falls out when the budget is reached is what has gone longest unused. */
    private final Map<Query, List<Candle>> results = new LinkedHashMap<>(16, 0.75f, true);

    private long cachedCandles;
    private int maxCachedCandles = DEFAULT_MAX_CACHED_CANDLES;
    private int minCachedResult = DEFAULT_MIN_CACHED_RESULT;

    public CachingCandleRepository(ReadCandleRepository base) {
        if (base == null) {
            throw new IllegalArgumentException("Nothing to cache");
        }

        this.base = base;
    }

    public CachingCandleRepository setMaxCachedCandles(int maxCachedCandles) {
        if (maxCachedCandles < 1) {
            throw new IllegalArgumentException("Cache must hold at least one candle, got " + maxCachedCandles);
        }

        synchronized (lock) {
            this.maxCachedCandles = maxCachedCandles;
            evict();
        }

        return this;
    }

    public CachingCandleRepository setMinCachedResult(int minCachedResult) {
        if (minCachedResult < 1) {
            throw new IllegalArgumentException("Minimum result must be at least one candle, got " + minCachedResult);
        }

        this.minCachedResult = minCachedResult;
        return this;
    }

    /** How many candles are held right now. */
    public long getCachedCandles() {
        synchronized (lock) {
            return cachedCandles;
        }
    }

    public void clear() {
        synchronized (lock) {
            results.clear();
            cachedCandles = 0;
        }
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        return remember(
            new Query(Call.BEFORE_OR_EQUAL, instrumentUid, at, null, amountBefore),
            () -> base.findBeforeOrEqual(instrumentUid, at, amountBefore)
        );
    }

    @Override
    public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
        return remember(
            new Query(Call.AFTER_OR_EQUAL, instrumentUid, at, null, amountAfter),
            () -> base.findAfterOrEqual(instrumentUid, at, amountAfter)
        );
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        return remember(
            new Query(Call.PERIOD, instrumentUid, from, to, 0),
            () -> base.getPeriod(instrumentUid, from, to)
        );
    }

    /**
     * Passed straight through, cache and lock alike. A price search answers with the one bar that
     * met the condition, so there is nothing here worth holding on to.
     */
    @Override
    public List<Candle> findByPrice(FindPriceParams params) {
        return base.findByPrice(params);
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        return base.getAt(instrumentUid, at);
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
        return base.getRangeMetadata(instrumentUid, from, to);
    }

    protected List<Candle> remember(Query query, Supplier<List<Candle>> read) {
        List<Candle> known = lookUp(query);

        if (known != null) {
            return known;
        }

        return store(query, List.copyOf(read.get()));
    }

    private List<Candle> lookUp(Query query) {
        synchronized (lock) {
            return results.get(query);
        }
    }

    /**
     * Keeps what was read, unless another thread got there first - in which case its answer stands,
     * so everyone asking the same question keeps getting the same list back.
     */
    private List<Candle> store(Query query, List<Candle> candles) {
        if (candles.size() < minCachedResult || candles.size() > maxCachedCandles) {
            return candles;
        }

        synchronized (lock) {
            List<Candle> known = results.putIfAbsent(query, candles);

            if (known != null) {
                return known;
            }

            cachedCandles += candles.size();
            evict();

            return candles;
        }
    }

    /** Drops the least recently used entries until the cache is back within its budget. */
    private void evict() {
        var entries = results.entrySet().iterator();

        while (cachedCandles > maxCachedCandles && entries.hasNext()) {
            cachedCandles -= entries.next().getValue().size();
            entries.remove();
        }
    }
}
