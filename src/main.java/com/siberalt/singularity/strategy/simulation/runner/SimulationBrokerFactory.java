package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.simulation.SimulationBroker;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.simulation.SimulationClock;

/**
 * Builds a broker for one simulation run.
 * <p>
 * Exists so that a comparison is a comparison. A run and the benchmark it is measured against need
 * brokers on identical terms - the same commission, spread, market impact, liquidity and latency -
 * and they cannot simply share one instance: a broker that has already run a strategy is still
 * carrying it, subscriptions and scheduled fills and all. Handing both runs the same factory is
 * what makes them equivalent without making them the same object, and it leaves exactly one place
 * where those terms are written down.
 * <p>
 * What must differ between runs is what the factory takes: each has its own clock, and its own
 * journals to write into.
 *
 * @param <B> the concrete broker built, so that a strategy needing more than the simulation
 *            contract offers can still be handed its run's broker without a cast
 */
@FunctionalInterface
public interface SimulationBrokerFactory<B extends SimulationBroker> {
    B create(
        SimulationClock clock,
        OrderRepository orderRepository,
        OperationRepository operationRepository
    );
}
