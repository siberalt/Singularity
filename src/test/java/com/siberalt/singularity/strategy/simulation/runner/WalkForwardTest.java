package com.siberalt.singularity.strategy.simulation.runner;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

        WalkForwardReport.Fold<String> first = report.folds().getFirst();
        assertEquals(START, first.trainFrom());
        assertEquals(START.plus(Duration.ofDays(4)), first.testFrom());
        assertEquals(START.plus(Duration.ofDays(6)), first.testTo());
        assertEquals(first.testFrom(), first.trainTo());

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
