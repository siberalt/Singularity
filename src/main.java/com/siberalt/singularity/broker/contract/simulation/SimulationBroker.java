package com.siberalt.singularity.broker.contract.simulation;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.simulation.SimulationUnit;

import java.util.Collection;

/**
 * A broker a strategy can be simulated against: it opens and funds accounts through its sandbox,
 * and it has parts that the {@code EventSimulator} has to drive - a market-data feed, a handler
 * filling orders once their moment arrives.
 * <p>
 * Which parts those are is the broker's own business; a runner only needs to hand them to the
 * simulator, which sorts out what each one is. That is the whole reason this interface exists:
 * without it a runner has to name a concrete broker to reach them, and stops being portable.
 */
public interface SimulationBroker extends SandboxServiceAwareBroker {
    /**
     * Everything about this broker that has to take part in a simulation run. Registered with the
     * simulator before it starts; the order within the collection is not significant.
     */
    Collection<SimulationUnit> getSimulationUnits();
}
