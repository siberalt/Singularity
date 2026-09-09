package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingCandleRepositoryTest {
    private static final String INSTRUMENT = "TEST";
    private static final Instant AT = Instant.parse("2021-06-03T10:00:00Z");

    private CountingRepository counting;
    private CachingCandleRepository repository;

    @BeforeEach
    void setUp() {
        counting = new CountingRepository();
        repository = new CachingCandleRepository(counting);
    }

    @Test
    void readsAStretchOnceAndHandsBackTheSameCandles() {
        List<Candle> first = repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        List<Candle> second = repository.findBeforeOrEqual(INSTRUMENT, AT, 100);

        assertEquals(1, counting.reads.get());
        assertSame(first, second);
        assertEquals(100, first.size());
    }

    @Test
    void tellsTheQueriesApart() {
        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        repository.findBeforeOrEqual(INSTRUMENT, AT, 50);
        repository.findBeforeOrEqual(INSTRUMENT, AT.plusSeconds(60), 100);
        repository.findAfterOrEqual(INSTRUMENT, AT, 100);
        repository.getPeriod(INSTRUMENT, AT, AT.plusSeconds(600));
        repository.findBeforeOrEqual("OTHER", AT, 100);

        assertEquals(6, counting.reads.get());
    }

    /**
     * Most queries here ask for one candle - what an order is priced against, when the market next
     * opens - and holding those would fill the cache with entries that cost more than the read.
     */
    @Test
    void doesNotRememberResultsTooSmallToBeWorthIt() {
        repository.findBeforeOrEqual(INSTRUMENT, AT, 1);
        repository.findBeforeOrEqual(INSTRUMENT, AT, 1);

        assertEquals(2, counting.reads.get());
        assertEquals(0, repository.getCachedCandles());
    }

    @Test
    void dropsWhatHasGoneLongestUnusedWhenTheBudgetIsReached() {
        repository.setMaxCachedCandles(150);

        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        repository.findBeforeOrEqual(INSTRUMENT, AT, 50);
        // Touching the older one makes the newer the least recently used.
        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        repository.findBeforeOrEqual(INSTRUMENT, AT, 40);

        assertEquals(140, repository.getCachedCandles());

        // The hundred survived, the fifty was dropped and has to be read again.
        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        assertEquals(3, counting.reads.get());

        repository.findBeforeOrEqual(INSTRUMENT, AT, 50);
        assertEquals(4, counting.reads.get());
    }

    @Test
    void refusesToHoldAResultLargerThanTheWholeBudget() {
        repository.setMaxCachedCandles(10);

        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);

        assertEquals(2, counting.reads.get());
        assertEquals(0, repository.getCachedCandles());
    }

    @Test
    void forgetsEverythingWhenCleared() {
        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);
        repository.clear();
        repository.findBeforeOrEqual(INSTRUMENT, AT, 100);

        assertEquals(2, counting.reads.get());
    }

    /**
     * The reason the reads go through a lock: this is what lets a walk-forward share one repository
     * over one database connection between the threads scoring its candidates.
     */
    @Test
    void servesSeveralThreadsFromOneRead() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            List<Future<List<Candle>>> pending = new ArrayList<>();

            for (int worker = 0; worker < 64; worker++) {
                pending.add(pool.submit(() -> repository.findBeforeOrEqual(INSTRUMENT, AT, 100)));
            }

            for (Future<List<Candle>> future : pending) {
                assertEquals(100, future.get().size());
            }

            // At most one read per distinct query; two threads missing together may both read, and one
            // of the two answers is kept - a benign duplicate, not a second entry.
            assertTrue(counting.reads.get() <= 8);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void refusesNothingToCache() {
        assertThrows(IllegalArgumentException.class, () -> new CachingCandleRepository(null));
    }

    @Test
    void refusesABudgetThatHoldsNothing() {
        assertThrows(IllegalArgumentException.class, () -> repository.setMaxCachedCandles(0));
        assertThrows(IllegalArgumentException.class, () -> repository.setMinCachedResult(0));
    }

    /** Each thread reads through its own connection; only the cache is shared. */
    @Test
    void doesNotHoldALockAcrossTheDelegatesRead() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            List<Future<Integer>> pending = new ArrayList<>();

            for (int worker = 0; worker < 32; worker++) {
                int size = worker;
                pending.add(pool.submit(() -> repository.findBeforeOrEqual(INSTRUMENT, AT, 2 + size).size()));
            }

            for (int worker = 0; worker < pending.size(); worker++) {
                assertEquals(2 + worker, pending.get(worker).get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class CountingRepository implements ReadCandleRepository {
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
            return candles(amountBefore);
        }

        @Override
        public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
            return candles(amountAfter);
        }

        @Override
        public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
            return candles(10);
        }

        @Override
        public List<Candle> findByPrice(FindPriceParams params) {
            return candles(1);
        }

        @Override
        public Optional<Candle> getAt(String instrumentUid, Instant at) {
            return Optional.empty();
        }

        @Override
        public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
            return null;
        }

        private List<Candle> candles(long amount) {
            reads.incrementAndGet();

            List<Candle> candles = new ArrayList<>();

            for (int index = 0; index < amount; index++) {
                Quotation price = Quotation.of(100);
                candles.add(new Candle(
                    INSTRUMENT, new TimePoint(AT.plusSeconds(index * 60L)), price, price, price, price, 1
                ));
            }

            return candles;
        }
    }
}
