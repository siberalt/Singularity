package com.siberalt.singularity.strategy.simulation.runner;

import java.time.Instant;
import java.util.List;

/**
 * What a strategy would have earned if nobody had known the future when its settings were chosen.
 * <p>
 * Only the test stretches count towards the result. The training numbers are here to be read
 * against them: a large gap between what a setting earned where it was chosen and what it earned
 * next is the measurement of how much of the choice was hindsight.
 *
 * @param <C> what varied between candidates
 */
public record WalkForwardReport<C>(List<Fold<C>> folds) {
    /**
     * One choice and what came of it. Three instants rather than four: training runs up to where
     * testing begins, so the boundary between them is one moment and is named once.
     *
     * @param trainFrom          start of the stretch the candidate was chosen on
     * @param testFrom           end of that stretch and start of the one it was judged on
     * @param testTo             end of the stretch it was judged on
     * @param chosen             the candidate that did best over the training stretch, and the only
     *                           thing carried forward into the test one
     * @param trainProfitPercent what it earned where it was chosen - not a result, a diagnostic
     * @param testProfitPercent  what it earned next, which is the only honest number here
     */
    public record Fold<C>(
        Instant trainFrom,
        Instant testFrom,
        Instant testTo,
        C chosen,
        double trainProfitPercent,
        double testProfitPercent
    ) {
    }

    /**
     * The stretch the compounded result covers: the first fold's test window through the last
     * fold's. Not the whole period the run was given - the history before the first test stretch
     * went into choosing, and nothing was earned on it.
     */
    public Instant testedFrom() {
        return folds.getFirst().testFrom();
    }

    public Instant testedTo() {
        return folds.getLast().testTo();
    }

    /**
     * The test stretches run end to end, as one account would have: each fold's return applied to
     * what the previous ones left. Averaging them instead would flatter a run that lost heavily
     * once, since a fold that halves the account cannot be undone by one that doubles it.
     */
    public double compoundedProfitPercent() {
        double value = 1;

        for (Fold<C> fold : folds) {
            value *= 1 + fold.testProfitPercent() / 100;
        }

        return (value - 1) * 100;
    }

    /** How often the choice made on the past worked on what followed. */
    public long profitableFolds() {
        return folds.stream().filter(fold -> fold.testProfitPercent() > 0).count();
    }

    /**
     * How much better a setting looked where it was picked than where it was used, averaged. The
     * size of the hindsight, in percentage points.
     */
    public double averageOptimismPoints() {
        return folds.stream()
            .mapToDouble(fold -> fold.trainProfitPercent() - fold.testProfitPercent())
            .average()
            .orElse(0);
    }
}
