package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.broker.shared.BrokerFacade;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.InMemoryOperationRepository;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.simulation.UserActionSimulator;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ConservativeStrategyRunner {
    private final String instrumentId;
    private final ReadCandleRepository candleRepository;
    private final ReadInstrumentRepository instrumentRepository;
    private final double brokerCommission;
    private final Money initialInvestment;

    public ConservativeStrategyRunner(
        String instrumentId,
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        double brokerCommission,
        Money initialInvestment
    ) {
        this.instrumentId = instrumentId;
        this.candleRepository = candleRepository;
        this.instrumentRepository = instrumentRepository;
        this.brokerCommission = brokerCommission;
        this.initialInvestment = initialInvestment;
    }

    public StrategyResult run(Instant startTime, Instant endTime) throws AbstractException {
        SimulationClock clock = new SimpleSimulationClock();
        EventMockBroker broker = createBroker(clock);

        TradeTiming timing = findTradePoints(startTime, endTime);

        StrategyBacktester backtester = new StrategyBacktester(
            (timeRange, accountId, observer) -> {
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

    private EventMockBroker createBroker(SimulationClock clock) {
        return EventMockBroker.builder(
                candleRepository,
                instrumentRepository,
                new InMemoryOrderRepository(),
                new InMemoryOperationRepository(),
                clock
            )
            .setCommissionRatio(brokerCommission)
            .build();
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
