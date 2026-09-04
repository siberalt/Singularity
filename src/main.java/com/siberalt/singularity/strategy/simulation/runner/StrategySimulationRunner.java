package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.broker.shared.BrokerFacade;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.observer.Observer;

import java.time.Duration;
import java.time.Instant;

public class StrategySimulationRunner {
    public static final String SIMULATION_ACCOUNT_NAME = "Account";

    private final StrategyStarter strategyStarter;
    private final EventMockBroker broker;
    private final String instrumentId;
    private final Money initialInvestment;
    private final SimulationClock clock;
    private final EventSimulatorInitializer eventSimulatorInitializer;

    public StrategySimulationRunner(StrategyStarter strategyStarter,
                                    EventMockBroker broker,
                                    String instrumentId,
                                    Money initialInvestment
    ) {
        this.strategyStarter = strategyStarter;
        this.broker = broker;
        this.instrumentId = instrumentId;
        this.initialInvestment = initialInvestment;
        this.clock = new SimpleSimulationClock();
        this.eventSimulatorInitializer = (timeRange, account, simulator) -> {};
    }

    public StrategySimulationRunner(StrategyStarter strategyStarter,
                                    EventMockBroker broker,
                                    String instrumentId,
                                    Money initialInvestment,
                                    SimulationClock clock
    ) {
        this.strategyStarter = strategyStarter;
        this.broker = broker;
        this.instrumentId = instrumentId;
        this.initialInvestment = initialInvestment;
        this.clock = clock;
        this.eventSimulatorInitializer = (timeRange, account, simulator) -> {};
    }

    public StrategySimulationRunner(StrategyStarter strategyStarter,
                                    EventMockBroker broker,
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

        Account account = broker.getUserService().openAccount(
            SIMULATION_ACCOUNT_NAME,
            AccountType.ORDINARY,
            AccessLevel.FULL_ACCESS
        );
        broker.getOperationsService().addMoney(account.getId(), initialInvestment);

        TimeRange timeRange = new TimeRange(startTime, endTime);
        eventSimulatorInitializer.initialize(timeRange, account, simulator);

        simulator.addSimulationUnit(broker.getPendingOrderHandler());
        simulator.addSimulationUnit(broker.getSubscriptionManager());
        simulator.addInitializableUnit(
            (start, end) -> strategyStarter.start(new TimeRange(start, end), account, new Observer())
        );

        Instant strategyBeginTime = Instant.now();
        simulator.run(startTime, endTime);
        Duration executionDuration = Duration.between(Instant.now(), strategyBeginTime);

        BrokerFacade brokerFacade = BrokerFacade.of(broker);

        brokerFacade.closePosition(account.getId(), instrumentId);

        Money balance = broker.getOperationsService().getAvailableMoney(account.getId(), initialInvestment.getCurrencyIso());
        Money profit = balance.subtract(initialInvestment);
        double profitPercent = profit.div(initialInvestment).multiply(100).getQuotation().toDouble();

        double apy = (profitPercent * 365) / Duration.between(startTime, endTime).toDays();

        return new StrategyResult(
            profit,
            profitPercent,
            balance,
            apy,
            executionDuration,
            account.getId()
        );
    }
}
