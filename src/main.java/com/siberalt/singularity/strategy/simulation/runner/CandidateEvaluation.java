package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

import java.time.Instant;

/**
 * Runs one candidate setting over one stretch of history and reports what it earned, as a
 * percentage of what it started with.
 * <p>
 * Kept as a plain function so that {@link WalkForward} does not care whether a candidate is a
 * period, a threshold or a whole strategy, nor whether the number comes from a full simulation or
 * from a measurement taken straight off the candles.
 *
 * @param <C> what varies between candidates
 */
@FunctionalInterface
public interface CandidateEvaluation<C> {
    double profitPercent(C candidate, Instant from, Instant to) throws AbstractException;
}
