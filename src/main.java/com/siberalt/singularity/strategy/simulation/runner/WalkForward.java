package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses a strategy's settings the way they would have had to be chosen at the time - on the past
 * only - and reports what they earned on what came next.
 * <p>
 * The problem it exists for: pick the best setting over a whole history and the result measures the
 * picking, not the strategy. On one share the best slope period earned 118 per cent over the second
 * half of its history while the next period along lost 25, and nothing available beforehand
 * separated them. Any single backtest is one draw from that spread, and the good draws are the ones
 * that get reported.
 * <p>
 * The window rolls rather than growing: each fold trains on the stretch immediately before its test
 * stretch and forgets what came earlier. A growing window lets a setting that suited one regime keep
 * winning the selection long after that regime ended, which is the failure this is meant to catch.
 */
public class WalkForward {
    public static final int DEFAULT_FOLDS = 6;

    /** Training stretch as a multiple of the test stretch. */
    public static final double DEFAULT_TRAIN_RATIO = 2;

    private int folds = DEFAULT_FOLDS;
    private double trainRatio = DEFAULT_TRAIN_RATIO;

    public WalkForward setFolds(int folds) {
        if (folds < 1) {
            throw new IllegalArgumentException("Need at least one fold, got " + folds);
        }

        this.folds = folds;
        return this;
    }

    public WalkForward setTrainRatio(double trainRatio) {
        if (trainRatio <= 0) {
            throw new IllegalArgumentException("Training stretch must be positive, got " + trainRatio);
        }

        this.trainRatio = trainRatio;
        return this;
    }

    /**
     * @param candidates the settings to choose between, all of them scored on every training stretch
     * @param evaluation how a candidate is scored over a stretch of history
     */
    public <C> WalkForwardReport<C> run(
        List<C> candidates,
        Instant start,
        Instant end,
        CandidateEvaluation<C> evaluation
    ) throws AbstractException {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Nothing to choose between");
        }

        Duration testSpan = Duration.between(start, end).dividedBy((long) (folds + trainRatio));
        Duration trainSpan = Duration.ofSeconds((long) (testSpan.toSeconds() * trainRatio));

        if (testSpan.isZero() || testSpan.isNegative()) {
            throw new IllegalArgumentException("The period is too short to split into " + folds + " folds");
        }

        List<WalkForwardReport.Fold<C>> results = new ArrayList<>();

        for (int fold = 0; fold < folds; fold++) {
            Instant testFrom = start.plus(trainSpan).plus(testSpan.multipliedBy(fold));
            Instant testTo = testFrom.plus(testSpan);
            Instant trainFrom = testFrom.minus(trainSpan);

            C chosen = null;
            double bestTrain = Double.NEGATIVE_INFINITY;

            for (C candidate : candidates) {
                double trained = evaluation.profitPercent(candidate, trainFrom, testFrom);

                if (trained > bestTrain) {
                    bestTrain = trained;
                    chosen = candidate;
                }
            }

            results.add(new WalkForwardReport.Fold<>(
                trainFrom,
                testFrom,
                testFrom,
                testTo,
                chosen,
                bestTrain,
                evaluation.profitPercent(chosen, testFrom, testTo)
            ));
        }

        return new WalkForwardReport<>(results);
    }
}
