package com.siberalt.singularity.strategy.simulation.runner;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkForwardTest {
    private static final Instant START = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2025-01-09T00:00:00Z");

    @Test
    void trainsOnWhatCameBeforeAndTestsOnWhatCameAfter() throws Exception {
        WalkForwardReport<String> report = new WalkForward()
            .setFolds(2)
            .setTrainRatio(2)
            .run(List.of("a"), START, END, (candidate, from, to) -> 0);

        // Eight days over two folds at twice the training: two-day tests, four-day training.
        assertEquals(2, report.folds().size());
        assertEquals(START.plus(Duration.ofDays(4)), report.testedFrom());
        assertEquals(START.plus(Duration.ofDays(8)), report.testedTo());

        WalkForwardReport.Fold<String> first = report.folds().getFirst();
        assertEquals(START, first.trainFrom());
        assertEquals(START.plus(Duration.ofDays(4)), first.testFrom());
        assertEquals(START.plus(Duration.ofDays(6)), first.testTo());

        WalkForwardReport.Fold<String> second = report.folds().getLast();
        assertEquals(first.testTo(), second.testFrom());
        assertEquals(START.plus(Duration.ofDays(2)), second.trainFrom());
    }

    /**
     * The whole point: the test stretch reports the candidate the training stretch liked, even when
     * another one was about to do better.
     */
    @Test
    void carriesForwardTheCandidateChosenOnTraining() throws Exception {
        List<String> asked = new ArrayList<>();

        WalkForwardReport<String> report = new WalkForward()
            .setFolds(1)
            .setTrainRatio(1)
            .run(List.of("good-then", "good-later"), START, END, (candidate, from, to) -> {
                asked.add(candidate);

                if (from.equals(START)) {
                    return candidate.equals("good-then") ? 10 : 1;
                }

                return candidate.equals("good-then") ? -50 : 90;
            });

        WalkForwardReport.Fold<String> fold = report.folds().getFirst();
        assertEquals("good-then", fold.chosen());
        assertEquals(10, fold.trainProfitPercent());
        assertEquals(-50, fold.testProfitPercent());
        assertEquals(60, report.averageOptimismPoints());
        assertTrue(asked.size() >= 3, "both candidates on training, the chosen one on test");
    }

    @Test
    void compoundsTheTestStretchesInsteadOfAveragingThem() throws Exception {
        double[] returns = {-50, 100};
        int[] index = {0};

        WalkForwardReport<String> report = new WalkForward()
            .setFolds(2)
            .setTrainRatio(2)
            .run(List.of("a"), START, END, (candidate, from, to) -> {
                boolean isTest = Duration.between(from, to).toDays() == 2;

                return isTest ? returns[Math.min(index[0]++, 1)] : 0;
            });

        // Halved then doubled is back where it started, which averaging to +25 per cent would hide.
        assertEquals(0, report.compoundedProfitPercent(), 1e-9);
        assertEquals(1, report.profitableFolds());
    }

    /**
     * A report that came out differently depending on how the threads were scheduled would be
     * worthless, so the scores are read back in the order the candidates were given and an equal
     * score leaves the earlier one in place.
     */
    @Test
    void givesTheSameReportOnAPoolAsOnOneThread() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            CandidateEvaluation<Integer> evaluation = (candidate, from, to) -> {
                // Uneven work, so the candidates finish in an order unrelated to their own.
                LockSupport.parkNanos(((17L * candidate) % 11) * 1_000_000);

                return candidate % 3;
            };
            List<Integer> candidates = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

            WalkForwardReport<Integer> sequential = new WalkForward().setFolds(3)
                .run(candidates, START, END, evaluation);
            WalkForwardReport<Integer> parallel = new WalkForward().setFolds(3).setExecutor(pool)
                .run(candidates, START, END, evaluation);

            // Candidates 2, 5 and 8 all score two; the first of them is the one that must win.
            assertEquals(2, sequential.folds().getFirst().chosen());
            assertEquals(sequential, parallel);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void scoresEveryCandidateOnEveryTrainingStretch() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            Set<String> scored = ConcurrentHashMap.newKeySet();

            new WalkForward().setFolds(2).setExecutor(pool).run(
                List.of("a", "b", "c"), START, END,
                (candidate, from, to) -> {
                    scored.add(candidate + "@" + from);

                    return 0;
                }
            );

            // Three candidates over two training stretches, plus the chosen one on each test.
            assertEquals(8, scored.size());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * A candidate that cannot run is scored out of the way, but an Error is not a candidate doing
     * badly - it is the machine in trouble, and it goes on through as itself.
     */
    @Test
    void letsAnErrorThroughRatherThanScoringIt() {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            assertThrows(StackOverflowError.class, () -> new WalkForward().setFolds(1).setExecutor(pool)
                .run(List.of("a"), START, END, (candidate, from, to) -> {
                    throw new StackOverflowError();
                }));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void takesAFailureOnAPoolAsAFailedFoldRatherThanAStoppedRun() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            WalkForwardReport<String> report = new WalkForward().setFolds(1).setExecutor(pool)
                .run(List.of("a"), START, END, (candidate, from, to) -> {
                    throw new IllegalStateException("nothing to trade");
                });

            assertEquals(1, report.failedFolds());
            assertTrue(report.folds().getFirst().failure().contains("nothing to trade"));
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * A setting that cannot run is not a setting that lost money. It is passed over, and the folds
     * where the others worked keep their results - which they used not to.
     */
    @Test
    void passesOverACandidateThatCannotRun() throws Exception {
        WalkForwardReport<String> report = new WalkForward()
            .setFolds(1)
            .setTrainRatio(1)
            .run(List.of("broken", "sound"), START, END, (candidate, from, to) -> {
                if (candidate.equals("broken")) {
                    throw new IllegalStateException("cannot size an order");
                }

                return 7;
            });

        WalkForwardReport.Fold<String> fold = report.folds().getFirst();
        assertEquals("sound", fold.chosen());
        assertEquals(7, fold.testProfitPercent());
        assertEquals(0, report.failedFolds());
    }

    @Test
    void marksAFoldFailedWhenTheChosenCandidateCannotBeTested() throws Exception {
        WalkForwardReport<String> report = new WalkForward()
            .setFolds(1)
            .setTrainRatio(1)
            .run(List.of("a"), START, END, (candidate, from, to) -> {
                if (from.equals(START)) {
                    return 12;
                }

                throw new IllegalStateException("ran out of money");
            });

        WalkForwardReport.Fold<String> fold = report.folds().getFirst();
        assertTrue(fold.hasFailed());
        assertTrue(fold.failure().contains("ran out of money"), fold.failure());
        // Neither a profit nor a loss: nothing happened, so the compounded result is untouched.
        assertEquals(0, fold.testProfitPercent());
        assertEquals(0, report.compoundedProfitPercent());
        assertEquals(1, report.failedFolds());
    }

    /** Only the folds that ran count towards the result; the one that could not leaves it alone. */
    @Test
    void keepsTheFoldsThatRanWhenOneDoesNot() throws Exception {
        WalkForwardReport<String> report = new WalkForward()
            .setFolds(2)
            .setTrainRatio(2)
            .run(List.of("a"), START, END, (candidate, from, to) -> {
                if (to.equals(END)) {
                    throw new IllegalStateException("data ran out");
                }

                return Duration.between(from, to).toDays() == 2 ? 50 : 0;
            });

        assertEquals(1, report.failedFolds());
        assertEquals(50, report.compoundedProfitPercent(), 1e-9);
    }

    @Test
    void refusesWithNothingToChooseBetween() {
        assertThrows(IllegalArgumentException.class,
            () -> new WalkForward().run(List.of(), START, END, (candidate, from, to) -> 0));
    }

    @Test
    void refusesAPeriodWithNothingToTest() {
        assertThrows(IllegalArgumentException.class,
            () -> new WalkForward().run(List.of("a"), START, START, (candidate, from, to) -> 0));
        assertThrows(IllegalArgumentException.class,
            () -> new WalkForward().run(List.of("a"), END, START, (candidate, from, to) -> 0));
    }
}
