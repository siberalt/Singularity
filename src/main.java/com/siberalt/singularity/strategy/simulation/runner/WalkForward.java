package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

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
    private Executor executor = Runnable::run;

    /**
     * Where the candidates of a fold are scored. The default runs them one after another on the
     * calling thread, which is the only safe assumption to make about somebody else's evaluation.
     * <p>
     * Handing it a pool is worth it: a backtest here spends under a tenth of its time reading
     * candles and the rest computing, so the work scales with cores. What the pool requires is that
     * the evaluation can be called from several threads at once - and the usual reason it cannot is
     * a shared database connection, which is not thread-safe. Give each run its own repository, or
     * read the candles into memory once and share them read-only.
     */
    public WalkForward setExecutor(Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("Executor cannot be null");
        }

        this.executor = executor;
        return this;
    }

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

            double[] trained = score(candidates, trainFrom, testFrom, evaluation);

            C chosen = candidates.getFirst();
            double bestTrain = trained[0];

            // Strictly greater, so an equal score leaves the earlier candidate in place and the
            // choice does not depend on the order the scores happened to finish in.
            for (int candidate = 1; candidate < candidates.size(); candidate++) {
                if (trained[candidate] > bestTrain) {
                    bestTrain = trained[candidate];
                    chosen = candidates.get(candidate);
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

    /**
     * Scores every candidate over one stretch, on {@link #setExecutor the executor} if one was
     * given. The candidates of a fold do not depend on each other, and a backtest spends its time
     * computing rather than waiting, so this is where the work parallelises.
     * <p>
     * The scores come back in the order the candidates were given whatever order they finished in,
     * which is what keeps the choice - and so the whole report - the same run to run.
     */
    protected <C> double[] score(
        List<C> candidates,
        Instant from,
        Instant to,
        CandidateEvaluation<C> evaluation
    ) throws AbstractException {
        List<CompletableFuture<Double>> pending = new ArrayList<>(candidates.size());

        for (C candidate : candidates) {
            pending.add(CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return evaluation.profitPercent(candidate, from, to);
                    } catch (AbstractException failed) {
                        throw new CompletionException(failed);
                    }
                },
                executor
            ));
        }

        double[] scores = new double[candidates.size()];

        for (int candidate = 0; candidate < scores.length; candidate++) {
            scores[candidate] = await(pending.get(candidate));
        }

        return scores;
    }

    /**
     * Unwraps what the evaluation threw, so a failure inside a worker reaches the caller as the
     * exception it was rather than wrapped in whatever carried it back.
     */
    private double await(CompletableFuture<Double> pending) throws AbstractException {
        try {
            return pending.join();
        } catch (CompletionException wrapped) {
            switch (wrapped.getCause()) {
                case AbstractException failed -> throw failed;
                case RuntimeException failed -> throw failed;
                case Error failed -> throw failed;
                case null, default -> throw wrapped;
            }
        }
    }
}
