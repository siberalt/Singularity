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
    private static final Instant START = Instant.parse("2021-06-03T10:00:00Z");

    private CountingRepository counting;
    private CachingCandleRepository repository;

    @BeforeEach
    void setUp() {
        counting = new CountingRepository();
        repository = new CachingCandleRepository(counting);
    }

    @Test
    void readsAStretchOnceAndHandsBackTheSameCandles() {
        List<Candle> first = repository.getPeriod(INSTRUMENT, START, minute(99));
        List<Candle> second = repository.getPeriod(INSTRUMENT, START, minute(99));

        assertEquals(1, counting.reads.get());
        assertEquals(100, first.size());
        assertEquals(first, second);
    }

    /**
     * The reason a stretch is kept rather than an answer. These are different questions over almost
     * the same history, which is what a walk-forward asks all day; an index of answers would call
     * every one of them a miss.
     */
    @Test
    void answersADifferentQuestionOverHistoryItAlreadyHas() {
        repository.getPeriod(INSTRUMENT, START, minute(999));

        // One more than asked for, both from the cache and from the repository behind it.
        assertEquals(11, repository.findBeforeOrEqual(INSTRUMENT, minute(500), 10).size());
        assertEquals(11, repository.findBeforeOrEqual(INSTRUMENT, minute(501), 10).size());
        assertEquals(20, repository.findAfterOrEqual(INSTRUMENT, minute(300), 20).size());
        assertEquals(minute(300), repository.getAt(INSTRUMENT, minute(300)).orElseThrow().getTime());
        assertEquals(50, repository.getPeriod(INSTRUMENT, minute(100), minute(149)).size());

        assertEquals(1, counting.reads.get());
    }

    @Test
    void handsBackTheSameCandleObjectToEveryResultHoldingIt() {
        repository.getPeriod(INSTRUMENT, START, minute(999));

        Candle fromOneWindow = repository.getPeriod(INSTRUMENT, minute(100), minute(199)).getFirst();
        Candle fromAnother = repository.findBeforeOrEqual(INSTRUMENT, minute(100), 1).getLast();

        assertSame(fromOneWindow, fromAnother);
        assertEquals(1000, repository.getCachedCandles());
    }

    /** Reading forward through a period has to leave one run behind, not a run per read. */
    @Test
    void mergesStretchesThatMeet() {
        repository.getPeriod(INSTRUMENT, START, minute(99));
        repository.getPeriod(INSTRUMENT, minute(50), minute(149));
        repository.getPeriod(INSTRUMENT, minute(120), minute(199));

        assertEquals(1, repository.getStretchCount(INSTRUMENT));
        assertEquals(200, repository.getCachedCandles());

        // The whole of it now answers without another read.
        assertEquals(200, repository.getPeriod(INSTRUMENT, START, minute(199)).size());
        assertEquals(3, counting.reads.get());
    }

    @Test
    void keepsStretchesThatDoNotMeetApart() {
        repository.getPeriod(INSTRUMENT, START, minute(99));
        repository.getPeriod(INSTRUMENT, minute(500), minute(599));

        assertEquals(2, repository.getStretchCount(INSTRUMENT));

        // Nothing is known about the gap, so a window spanning it is read afresh.
        repository.getPeriod(INSTRUMENT, minute(50), minute(550));
        assertEquals(3, counting.reads.get());
        assertEquals(1, repository.getStretchCount(INSTRUMENT));
    }

    /**
     * What lies before a run's start is unknown, not absent, so a lookback reaching past it is a
     * miss rather than a short answer.
     */
    @Test
    void doesNotAnswerFromHistoryItDoesNotHave() {
        repository.getPeriod(INSTRUMENT, minute(100), minute(199));

        repository.findBeforeOrEqual(INSTRUMENT, minute(150), 500);

        assertEquals(2, counting.reads.get());
    }

    @Test
    void tellsInstrumentsApart() {
        repository.getPeriod(INSTRUMENT, START, minute(99));
        repository.getPeriod("OTHER", START, minute(99));

        assertEquals(2, counting.reads.get());
        assertEquals(1, repository.getStretchCount(INSTRUMENT));
        assertEquals(1, repository.getStretchCount("OTHER"));
    }

    /**
     * Most queries here ask for one candle - what an order is priced against, when the market next
     * opens - and the two the repository answers with serve nothing later.
     */
    @Test
    void doesNotKeepReadsTooShortToBeWorthIt() {
        repository.findBeforeOrEqual(INSTRUMENT, minute(500), 1);
        repository.findBeforeOrEqual(INSTRUMENT, minute(500), 1);

        assertEquals(2, counting.reads.get());
        assertEquals(0, repository.getCachedCandles());
    }

    /**
     * Trimming rather than emptying: a run stays complete as it shrinks, so what is lost is the
     * oldest history and not the ability to answer at all.
     */
    @Test
    void dropsTheOldestCandlesWhenTheBudgetIsReached() {
        repository.setMaxCachedCandles(150);

        repository.getPeriod(INSTRUMENT, START, minute(99));
        repository.getPeriod(INSTRUMENT, minute(90), minute(189));

        assertEquals(150, repository.getCachedCandles());
        assertEquals(1, repository.getStretchCount(INSTRUMENT));

        // The recent half still answers; the trimmed-off start does not.
        assertEquals(50, repository.getPeriod(INSTRUMENT, minute(140), minute(189)).size());
        assertEquals(2, counting.reads.get());

        repository.getPeriod(INSTRUMENT, START, minute(49));
        assertEquals(3, counting.reads.get());
    }

    @Test
    void refusesToHoldAReadLargerThanTheWholeBudget() {
        repository.setMaxCachedCandles(10);

        repository.getPeriod(INSTRUMENT, START, minute(99));
        repository.getPeriod(INSTRUMENT, START, minute(99));

        assertEquals(2, counting.reads.get());
        assertEquals(0, repository.getCachedCandles());
    }

    @Test
    void forgetsEverythingWhenCleared() {
        repository.getPeriod(INSTRUMENT, START, minute(99));
        repository.clear();
        repository.getPeriod(INSTRUMENT, START, minute(99));

        assertEquals(2, counting.reads.get());
        assertEquals(100, repository.getCachedCandles());
    }

    /**
     * Reads run without the lock, so this only asserts that the answers are right and that the
     * stretch is not read once per thread. Two threads missing together may both read.
     */
    @Test
    void servesSeveralThreadsFromOneStretch() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            List<Future<Integer>> pending = new ArrayList<>();

            for (int worker = 0; worker < 64; worker++) {
                pending.add(pool.submit(() -> repository.getPeriod(INSTRUMENT, START, minute(999)).size()));
            }

            for (Future<Integer> answered : pending) {
                assertEquals(1000, answered.get());
            }

            assertTrue(counting.reads.get() <= 8, "read at most once per thread, not once per call");
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

    private static Instant minute(int offset) {
        return START.plusSeconds(offset * 60L);
    }

    /** Answers as a real repository would: whole minutes on a fixed grid, counted per read. */
    private static final class CountingRepository implements ReadCandleRepository {
        private final AtomicInteger reads = new AtomicInteger();

        @Override
        public List<Candle> findBeforeOrEqual(String instrumentUid, Instant at, long amountBefore) {
            reads.incrementAndGet();

            // The candle at the moment asked about and amountBefore before it, as the sqlite
            // repository does - it queries one row more than it is asked for.
            int last = offsetOf(at);
            int first = (int) Math.max(0, last - amountBefore);

            return grid(instrumentUid, first, last);
        }

        @Override
        public List<Candle> findAfterOrEqual(String instrumentUid, Instant at, long amountAfter) {
            reads.incrementAndGet();

            int first = offsetOf(at);

            return grid(instrumentUid, first, (int) (first + amountAfter - 1));
        }

        @Override
        public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
            reads.incrementAndGet();

            return grid(instrumentUid, offsetOf(from), offsetOf(to));
        }

        @Override
        public List<Candle> findByPrice(FindPriceParams params) {
            reads.incrementAndGet();

            return List.of();
        }

        @Override
        public Optional<Candle> getAt(String instrumentUid, Instant at) {
            reads.incrementAndGet();

            return grid(instrumentUid, offsetOf(at), offsetOf(at)).stream().findFirst();
        }

        @Override
        public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
            return null;
        }

        private int offsetOf(Instant at) {
            return (int) ((at.toEpochMilli() - START.toEpochMilli()) / 60_000);
        }

        private List<Candle> grid(String instrumentUid, int first, int last) {
            List<Candle> candles = new ArrayList<>();

            for (int offset = Math.max(0, first); offset <= last; offset++) {
                Quotation price = Quotation.of(100);
                candles.add(new Candle(
                    instrumentUid, new TimePoint(minute(offset)), price, price, price, price, 1
                ));
            }

            return candles;
        }
    }
}
