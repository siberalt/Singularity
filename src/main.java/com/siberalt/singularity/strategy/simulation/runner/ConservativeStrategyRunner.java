package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.simulation.SimulationBroker;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.shared.BrokerFacade;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.operation.InMemoryOperationRepository;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.simulation.UserActionSimulator;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Buy at the start of the period, sell at the end - what the instrument would have paid for simply
 * being held. The benchmark any strategy has to beat to have been worth running.
 * <p>
 * Its broker comes from the same factory as the one the strategy was measured with, so both are on
 * identical execution terms. It has to be a separate instance rather than the strategy's own: that
 * one is still carrying its run - the strategy stays subscribed to its candles and the pending
 * order handler still holds its scheduled fills - so replaying the period on it would have the
 * strategy trading through the benchmark.
 */
public class ConservativeStrategyRunner {
    private final String instrumentId;
    private final ReadCandleRepository candleRepository;
    private final SimulationBrokerFactory<?> brokerFactory;
    private final Money initialInvestment;

    public ConservativeStrategyRunner(
        String instrumentId,
        ReadCandleRepository candleRepository,
        SimulationBrokerFactory<?> brokerFactory,
        Money initialInvestment
    ) {
        this.instrumentId = instrumentId;
        this.candleRepository = candleRepository;
        this.brokerFactory = brokerFactory;
        this.initialInvestment = initialInvestment;
    }

    public StrategyResult run(Instant startTime, Instant endTime) throws AbstractException {
        SimulationClock clock = new SimpleSimulationClock();
        SimulationBroker broker = brokerFactory.create(
            clock,
            new InMemoryOrderRepository(),
            new InMemoryOperationRepository()
        );

        TradeTiming timing = findTradePoints(startTime, endTime);

        StrategyBacktester<SimulationBroker> backtester = new StrategyBacktester<>(
            (timeRange, accountId, runBroker, observer) -> {
            },
            broker,
            instrumentId,
            initialInvestment,
            clock,
            (timeRange, accountId, eventSimulator) -> {
                UserActionSimulator<Map<String, Object>> actionSimulator = new UserActionSimulator<>(new HashMap<>());
                actionSimulator.planAction(timing.buyTime(), x ->
                    BrokerFacade.of(broker).buyFullBalanceUnchecked(accountId, instrumentId));
                actionSimulator.planAction(timing.sellTime(), x ->
                    BrokerFacade.of(broker).closePositionUnchecked(accountId, instrumentId));
                eventSimulator.addSimulationUnit(actionSimulator);
            }
        );

        return backtester.run(startTime, endTime);
    }

    private TradeTiming findTradePoints(Instant startTime, Instant endTime) {
        Instant buyTime = candleRepository.findAfterOrEqual(instrumentId, startTime, 1)
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No buy candle found"))
            .getTime();

        Instant sellTime = candleRepository.findBeforeOrEqual(instrumentId, endTime, 1)
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No sell candle found"))
            .getTime();

        return new TradeTiming(instrumentId, buyTime, sellTime);
    }

    private record TradeTiming(
        String instrumentId,
        Instant buyTime,
        Instant sellTime
    ) {
    }
}
