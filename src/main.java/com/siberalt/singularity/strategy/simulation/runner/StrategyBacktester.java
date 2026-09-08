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
public class StrategyBacktester<B extends SimulationBroker> {
    public static final String BACKTEST_ACCOUNT_NAME = "Account";

    private final StrategyStarter<B> strategyStarter;
    private final B broker;
    private final String instrumentId;
    private final Money initialInvestment;
    private final SimulationClock clock;
    private final EventSimulatorInitializer eventSimulatorInitializer;

    public StrategyBacktester(StrategyStarter<B> strategyStarter,
                                    B broker,
                                    String instrumentId,
                                    Money initialInvestment
    ) {
        this(strategyStarter, broker, instrumentId, initialInvestment, new SimpleSimulationClock());
    }

    public StrategyBacktester(StrategyStarter<B> strategyStarter,
                                    B broker,
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

    public StrategyBacktester(StrategyStarter<B> strategyStarter,
                              B broker,
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
            (start, end) -> strategyStarter.start(new TimeRange(start, end), accountId, broker, new Observer())
        );

        Instant strategyBeginTime = Instant.now();
        simulator.run(startTime, endTime);
        Duration executionDuration = Duration.between(strategyBeginTime, Instant.now());

        Money balance = finalValue(brokerFacade, accountId);
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

    /**
     * What the account is worth when the run ends: its money, free and reserved alike, plus whatever
     * it still holds valued at the last price the market showed.
     * <p>
     * Marked to market rather than sold off. Selling was the obvious thing and it was wrong: the
     * sale had to happen after the simulation had stopped, so under any liquidity limit only the
     * first slice of it could fill and the rest of the position was silently valued at nothing - a
     * buy-and-hold, fully invested by construction, came out at minus ninety per cent. Trying to
     * sell inside the run instead only moves the problem to guessing how many bars before the end
     * to start.
     * <p>
     * It is also the fairer measure. A strategy is worth what its portfolio is worth; a forced sale
     * in the closing minutes is an event that belongs to the backtest, not to the strategy.
     */
    protected Money finalValue(SandboxBrokerFacade brokerFacade, String accountId) throws AbstractException {
        String currencyIso = initialInvestment.getCurrencyIso();
        Money money = brokerFacade.getAvailableMoney(accountId, currencyIso)
            .add(brokerFacade.getBlockedMoney(accountId, currencyIso));

        long heldLots = brokerFacade.getHeldPositionSize(accountId, instrumentId);

        if (heldLots == 0) {
            return money;
        }

        return money.add(
            Money.of(currencyIso, brokerFacade.getLastPrice(instrumentId).multiply(heldLots))
        );
    }
}
