package com.siberalt.singularity.entity.candle;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the stretches of history it has already read, and answers from them whatever query falls
 * inside one.
 * <p>
 * Reading candles is cheap; turning them into objects is not. A walk-forward scores every candidate
 * over the same training stretch and then the winner over the same test stretch, so a ten-fold run
 * over nine candidates rebuilt the same hundred thousand candles a hundred times over. The database
 * was never the cost - a full read of four hundred thousand candles takes under a third of a second
 * - but the objects it produces are, both to build and for the collector to clear afterwards.
 * <p>
 * What is kept is a run of candles per stretch of history, not an answer per question. Remembering
 * answers only helps a question asked twice word for word; a stretch helps every question landing
 * inside it, and the questions here overlap far more than they repeat - the same lookback taken one
 * bar later is a different question over almost the same history. Stretches that meet are merged,
 * so reading forward through a period leaves one run behind rather than a hundred.
 * <p>
 * That also settles where the candles live. Each row is built once, held once and handed to every
 * result containing it, because the runs <em>are</em> the storage: there is no index of answers
 * beside them to keep in step.
 * <p>
 * A pool keyed by instrument and time, in a static factory on {@link Candle}, would be the obvious
 * way to share rows instead. It cannot go there: those two do not identify a candle in this code.
 * {@link CandleAggregator} builds an hourly bar carrying the instrument and timestamp of the first
 * minute inside it, so an interning factory would hand back that minute in place of the hour. A
 * cache over one repository only ever sees rows at one interval, and the ambiguity does not arise.
 * <p>
 * Thread-safe as far as its own bookkeeping goes, and no further: the delegate is called without a
 * lock, so it has to stand being called from several threads at once - see
 * {@link ThreadLocalCandleRepository}. Holding the lock across the read was tried and cost more
 * than the cache saved, because a great many calls here ask for a single candle, miss whatever is
 * kept, and under a shared lock turn a parallel run into a queue.
 */
public class CachingCandleRepository implements ReadCandleRepository {
    /** Roughly a gigabyte of candles, and bounded because a cache that grows until the heap runs out is not one. */
    public static final int DEFAULT_MAX_CACHED_CANDLES = 2_000_000;

    /**
     * Reads shorter than this are answered but not kept. Three, not two, because the commonest call
     * in the system is a lookback of one - what is this order priced against - and the repository
     * answers it with two candles; a run that short serves nothing later and would leave a scrap
     * behind for every order priced.
     */
    public static final int DEFAULT_MIN_CACHED_RESULT = 3;

    private final ReadCandleRepository base;
    private final Object lock = new Object();

    /** Runs of candles per instrument, disjoint and in time order. */
    private final Map<String, List<Stretch>> stretches = new HashMap<>();

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
            trim();
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

    /** Candles held right now, each counted once however many results contain it. */
    public long getCachedCandles() {
        synchronized (lock) {
            return cachedCandles;
        }
    }

    /** Runs held for an instrument. More than one means the reads so far have not met. */
    public int getStretchCount(String instrumentUid) {
        synchronized (lock) {
            return stretches.getOrDefault(instrumentUid, List.of()).size();
        }
    }

    public void clear() {
        synchronized (lock) {
            stretches.clear();
            cachedCandles = 0;
        }
    }

    @Override
    public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
        List<Candle> known = fromStretch(instrumentUid, at, stretch -> stretch.endingAt(at, amountBefore));

        if (known != null) {
            return known;
        }

        List<Candle> candles = base.findBeforeOrEqual(instrumentUid, at, amountBefore);

        // Complete from the oldest candle it found up to the moment asked about: nothing inside
        // that window was left out, whatever lies before it.
        return keep(instrumentUid, candles, candles.isEmpty() ? at : candles.getFirst().getTime(), at);
    }

    @Override
    public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
        List<Candle> known = fromStretch(instrumentUid, at, stretch -> stretch.startingAt(at, amountAfter));

        if (known != null) {
            return known;
        }

        List<Candle> candles = base.findAfterOrEqual(instrumentUid, at, amountAfter);

        return keep(instrumentUid, candles, at, candles.isEmpty() ? at : candles.getLast().getTime());
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        synchronized (lock) {
            Stretch covering = find(instrumentUid, from, to);

            if (covering != null) {
                return covering.between(from, to);
            }
        }

        // The window asked about is what becomes known, not merely the window the candles landed
        // in: asking again from a moment before the first candle must not count as a miss.
        return keep(instrumentUid, base.getPeriod(instrumentUid, from, to), from, to);
    }

    /**
     * Passed straight through. A price search answers with the one bar that met the condition,
     * which says nothing about the bars in between and so cannot be kept as a stretch.
     */
    @Override
    public List<Candle> findByPrice(FindPriceParams params) {
        return base.findByPrice(params);
    }

    @Override
    public Optional<Candle> getAt(String instrumentUid, Instant at) {
        synchronized (lock) {
            Stretch covering = find(instrumentUid, at, at);

            if (covering != null) {
                return covering.at(at);
            }
        }

        return base.getAt(instrumentUid, at);
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
        return base.getRangeMetadata(instrumentUid, from, to);
    }

    /**
     * Files what was read as a run complete over {@code [from, to]}, merging it into any run it
     * meets, and hands back the candles as the cache now holds them.
     */
    protected List<Candle> keep(String instrumentUid, List<Candle> candles, Instant from, Instant to) {
        if (candles.size() < minCachedResult || candles.size() > maxCachedCandles) {
            return candles;
        }

        synchronized (lock) {
            List<Stretch> known = stretches.computeIfAbsent(instrumentUid, uid -> new ArrayList<>());
            Stretch merged = new Stretch(from, to, List.copyOf(candles));

            for (var others = known.iterator(); others.hasNext(); ) {
                Stretch other = others.next();

                if (merged.meets(other)) {
                    cachedCandles -= other.candles().size();
                    merged = merged.merge(other);
                    others.remove();
                }
            }

            known.add(merged);
            known.sort(Comparator.comparing(Stretch::from));
            cachedCandles += merged.candles().size();
            trim();

            return merged.between(from, to);
        }
    }

    private List<Candle> fromStretch(String instrumentUid, Instant at, StretchQuery query) {
        synchronized (lock) {
            Stretch stretch = find(instrumentUid, at, at);

            return stretch == null ? null : query.answer(stretch);
        }
    }

    /** The run covering the whole of {@code [from, to]}, or null when no single one does. */
    private Stretch find(String instrumentUid, Instant from, Instant to) {
        for (Stretch stretch : stretches.getOrDefault(instrumentUid, List.of())) {
            if (stretch.covers(from, to)) {
                return stretch;
            }
        }

        return null;
    }

    /**
     * Drops candles from the older end of the longest-reaching runs until the cache is back within
     * its budget. A run stays complete as it shrinks - only its start moves - so trimming costs the
     * recall of the oldest history rather than the right to answer at all, which emptying the cache
     * would.
     */
    private void trim() {
        while (cachedCandles > maxCachedCandles) {
            Stretch oldest = null;
            List<Stretch> holder = null;

            for (var held : stretches.entrySet()) {
                for (Stretch stretch : held.getValue()) {
                    if (oldest == null || stretch.from().isBefore(oldest.from())) {
                        oldest = stretch;
                        holder = held.getValue();
                    }
                }
            }

            if (oldest == null) {
                return;
            }

            int drop = (int) Math.min(cachedCandles - maxCachedCandles, oldest.candles().size());
            Stretch dropped = oldest;

            cachedCandles -= drop;
            holder.removeIf(stretch -> stretch == dropped);

            if (drop < oldest.candles().size()) {
                holder.add(oldest.withoutFirst(drop));
                holder.sort(Comparator.comparing(Stretch::from));
            }
        }
    }

    @FunctionalInterface
    private interface StretchQuery {
        List<Candle> answer(Stretch stretch);
    }

    /**
     * A run of candles known to be complete over {@code [from, to]}. The window can reach past the
     * candles themselves - a period asked about before the instrument had any data is still a
     * period in which nothing was missed.
     */
    private record Stretch(Instant from, Instant to, List<Candle> candles) {
        boolean covers(Instant queryFrom, Instant queryTo) {
            return !queryFrom.isBefore(from) && !queryTo.isAfter(to);
        }

        boolean meets(Stretch other) {
            return !other.from().isAfter(to) && !from.isAfter(other.to());
        }

        Stretch merge(Stretch other) {
            Stretch first = from.isBefore(other.from()) ? this : other;
            Stretch second = first == this ? other : this;
            List<Candle> united = new ArrayList<>(first.candles());

            for (Candle candle : second.candles()) {
                if (united.isEmpty() || candle.getTime().isAfter(united.getLast().getTime())) {
                    united.add(candle);
                }
            }

            return new Stretch(
                first.from(),
                to.isAfter(other.to()) ? to : other.to(),
                List.copyOf(united)
            );
        }

        Stretch withoutFirst(int amount) {
            List<Candle> left = List.copyOf(candles.subList(amount, candles.size()));

            return new Stretch(left.getFirst().getTime(), to, left);
        }

        List<Candle> between(Instant queryFrom, Instant queryTo) {
            return List.copyOf(candles.subList(after(queryFrom), after(queryTo.plusMillis(1))));
        }

        /**
         * The candle at {@code at} together with {@code amount} before it, or null when the run
         * does not reach back far enough - what lies before its start is unknown, not absent.
         * <p>
         * One more than asked for, because that is what the repository behind this hands back:
         * {@code SqliteCandleRepository} queries {@code amountBefore + 1} rows. A cache that
         * returned a different number would quietly change what every caller sees, which is the one
         * thing a cache may never do.
         */
        List<Candle> endingAt(Instant at, long amount) {
            long wanted = amount + 1;
            int end = after(at.plusMillis(1));

            return end < wanted ? null : List.copyOf(candles.subList(end - (int) wanted, end));
        }

        List<Candle> startingAt(Instant at, long amount) {
            int start = after(at);

            return candles.size() - start < amount
                ? null
                : List.copyOf(candles.subList(start, start + (int) amount));
        }

        Optional<Candle> at(Instant time) {
            int index = after(time);

            return index < candles.size() && candles.get(index).getTime().equals(time)
                ? Optional.of(candles.get(index))
                : Optional.empty();
        }

        /** Index of the first candle at or after {@code time}. */
        private int after(Instant time) {
            int low = 0;
            int high = candles.size();

            while (low < high) {
                int middle = (low + high) >>> 1;

                if (candles.get(middle).getTime().isBefore(time)) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }

            return low;
        }
    }
}
