package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.simulation.SimulationBroker;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.shared.SandboxBrokerFacade;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.observer.Observer;

import java.time.Duration;
import java.time.Instant;

/**
 * Backtests a strategy: replays it against any {@link SimulationBroker} over a past time range on a
 * freshly opened and funded account, then reports what it earned. Everything it needs from the
 * broker goes through the contract - the sandbox opens and funds the account, the broker names its
 * own simulation units - so the same backtest works for any broker that can be simulated, not just
 * the mock one.
 */
public class StrategyBacktester {
    public static final String BACKTEST_ACCOUNT_NAME = "Account";

    private final StrategyStarter strategyStarter;
    private final SimulationBroker broker;
    private final String instrumentId;
    private final Money initialInvestment;
    private final SimulationClock clock;
    private final EventSimulatorInitializer eventSimulatorInitializer;

    public StrategyBacktester(StrategyStarter strategyStarter,
                                    SimulationBroker broker,
                                    String instrumentId,
                                    Money initialInvestment
    ) {
        this(strategyStarter, broker, instrumentId, initialInvestment, new SimpleSimulationClock());
    }

    public StrategyBacktester(StrategyStarter strategyStarter,
                                    SimulationBroker broker,
                                    String instrumentId,
                                    Money initialInvestment,
                                    SimulationClock clock
    ) {
        this(
            strategyStarter,
            broker,
            instrumentId,
            initialInvestment,
            clock,
            (timeRange, accountId, simulator) -> {}
        );
    }

    public StrategyBacktester(StrategyStarter strategyStarter,
                                    SimulationBroker broker,
                                    String instrumentId,
                                    Money initialInvestment,
                                    SimulationClock clock,
                                    EventSimulatorInitializer eventSimulatorInitializer
    ) {
        this.strategyStarter = strategyStarter;
        this.broker = broker;
        this.instrumentId = instrumentId;
        this.initialInvestment = initialInvestment;
        this.clock = clock;
        this.eventSimulatorInitializer = eventSimulatorInitializer;
    }

    public StrategyResult run(Instant startTime, Instant endTime) throws AbstractException {
        EventSimulator simulator = new EventSimulator(clock);
        SandboxBrokerFacade brokerFacade = SandboxBrokerFacade.of(broker);

        String accountId = brokerFacade.openAccount(BACKTEST_ACCOUNT_NAME);
        brokerFacade.payIn(accountId, initialInvestment);

        TimeRange timeRange = new TimeRange(startTime, endTime);
        eventSimulatorInitializer.initialize(timeRange, accountId, simulator);

        broker.getSimulationUnits().forEach(simulator::addSimulationUnit);
        simulator.addInitializableUnit(
            (start, end) -> strategyStarter.start(new TimeRange(start, end), accountId, new Observer())
        );

        Instant strategyBeginTime = Instant.now();
        simulator.run(startTime, endTime);
        Duration executionDuration = Duration.between(Instant.now(), strategyBeginTime);

        brokerFacade.closePosition(accountId, instrumentId);

        Money balance = brokerFacade.getAvailableMoney(accountId, initialInvestment.getCurrencyIso());
        Money profit = balance.subtract(initialInvestment);
        double profitPercent = profit.div(initialInvestment).multiply(100).getQuotation().toDouble();

        double apy = (profitPercent * 365) / Duration.between(startTime, endTime).toDays();

        return new StrategyResult(
            profit,
            profitPercent,
            balance,
            apy,
            executionDuration,
            accountId
        );
    }
}
