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
     * <p>
     * The pool stays the caller's to close, and closing it belongs in a {@code finally}. Its
     * workers are not daemons, so a run that ends by throwing leaves them idle and the machine
     * alive with nothing happening and nothing printed - which reads exactly like a deadlock and
     * costs an afternoon to tell apart from one.
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

        List<Window> windows = new ArrayList<>(folds);

        for (int fold = 0; fold < folds; fold++) {
            // Each fold starts one test stretch after the one before, and the first starts at the
            // beginning: what follows its training is the first stretch anything is earned on.
            Instant trainFrom = start.plus(testSpan.multipliedBy(fold));
            Instant testFrom = trainFrom.plus(trainSpan);

            windows.add(new Window(trainFrom, testFrom, testFrom.plus(testSpan)));
        }

        // Every training run of every fold at once: none of them depends on any other, and a fold
        // whose slowest candidate holds up the rest would otherwise set the pace on its own.
        List<CompletableFuture<Double>> training = new ArrayList<>(folds * candidates.size());

        for (Window window : windows) {
            for (C candidate : candidates) {
                training.add(submit(evaluation, candidate, window.trainFrom(), window.testFrom()));
            }
        }

        List<C> chosen = new ArrayList<>(folds);
        List<Double> bestTrain = new ArrayList<>(folds);
        List<CompletableFuture<Double>> testing = new ArrayList<>(folds);

        for (int fold = 0; fold < folds; fold++) {
            C best = candidates.getFirst();
            double bestScore = score(training.get(fold * candidates.size()));

            // Strictly greater, so an equal score leaves the earlier candidate in place and the
            // choice does not depend on the order the scores happened to finish in.
            for (int candidate = 1; candidate < candidates.size(); candidate++) {
                double score = score(training.get(fold * candidates.size() + candidate));

                if (score > bestScore) {
                    bestScore = score;
                    best = candidates.get(candidate);
                }
            }

            chosen.add(best);
            bestTrain.add(bestScore);
            testing.add(submit(evaluation, best, windows.get(fold).testFrom(), windows.get(fold).testTo()));
        }

        List<WalkForwardReport.Fold<C>> results = new ArrayList<>(folds);

        for (int fold = 0; fold < folds; fold++) {
            Window window = windows.get(fold);
            String failure = failureOf(testing.get(fold));

            results.add(new WalkForwardReport.Fold<>(
                window.trainFrom(),
                window.testFrom(),
                window.testTo(),
                chosen.get(fold),
                bestTrain.get(fold),
                failure == null ? testing.get(fold).join() : 0,
                failure
            ));
        }

        return new WalkForwardReport<>(results);
    }

    /**
     * Hands one evaluation to {@link #setExecutor the executor}, which by default is the calling
     * thread. Nothing is read back here: everything is submitted first, so the runs overlap, and
     * the results are collected afterwards in the order they were submitted.
     */
    protected <C> CompletableFuture<Double> submit(
        CandidateEvaluation<C> evaluation,
        C candidate,
        Instant from,
        Instant to
    ) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    return evaluation.profitPercent(candidate, from, to);
                } catch (AbstractException failed) {
                    throw new CompletionException(failed);
                }
            },
            executor
        );
    }

    /** One fold's stretches. The training one runs up to where the test one starts. */
    private record Window(Instant trainFrom, Instant testFrom, Instant testTo) {
    }

    /**
     * What a candidate scored over a training stretch, or the worst possible score if it could not
     * run at all.
     * <p>
     * A setting that blows the account up mid-order or runs off the end of the data is not a
     * setting that lost money - it is one that cannot be used, and the choice should pass it over
     * rather than the whole run stopping on it. Nine folds' worth of honest work used to be thrown
     * away because a tenth candidate on one stretch could not size an order.
     */
    protected double score(CompletableFuture<Double> pending) {
        return failureOf(pending) == null ? pending.join() : Double.NEGATIVE_INFINITY;
    }

    /**
     * Why this run could not finish, in a line, or null when it did. Errors are left to propagate:
     * an evaluation out of memory is not a candidate scoring badly.
     */
    protected String failureOf(CompletableFuture<Double> pending) {
        try {
            pending.join();

            return null;
        } catch (CompletionException wrapped) {
            Throwable cause = wrapped.getCause() == null ? wrapped : wrapped.getCause();

            if (cause instanceof Error error) {
                throw error;
            }

            return cause.toString();
        }
    }
}
