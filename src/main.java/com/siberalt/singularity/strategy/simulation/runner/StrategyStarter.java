package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.simulation.SimulationBroker;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.observer.Observer;

/**
 * Builds and runs the strategy under test for one backtest.
 * <p>
 * The broker is handed in rather than captured, because it belongs to the run and not to the
 * caller: every run needs its own - a broker that has already replayed a period is still carrying
 * it, subscriptions and scheduled fills and all - and the runner is the only one that knows which
 * run this is. Closing over a broker built outside forced the caller to hand the same object to
 * two places at once, to the strategy and to the runner, and nothing checked that they matched.
 *
 * @param <B> the kind of broker the strategy needs, so that a strategy wanting more than the
 *            simulation contract - event subscriptions, say - can ask for it without a cast
 */
@FunctionalInterface
public interface StrategyStarter<B extends SimulationBroker> {
    void start(TimeRange timeRange, String accountId, B broker, Observer observer);
}
