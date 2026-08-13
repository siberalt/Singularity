package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.simulation.SimulationClock;

import java.time.Instant;

public class EffectivenessAnalyzer {
    private final StrategyStarter strategyStarter;
    private final String instrumentId;
    private final Money initialInvestment;
    private final ReadCandleRepository candleRepository;
    private final ReadInstrumentRepository instrumentRepository;
    private final EventMockBroker broker;
    private final SimulationClock clock;
    private final double brokerCommission;

    public EffectivenessAnalyzer(
        StrategyStarter strategyStarter,
        String instrumentId,
        Money initialInvestment,
        ReadCandleRepository candleRepository,
        ReadInstrumentRepository instrumentRepository,
        EventMockBroker broker,
        SimulationClock clock,
        double brokerCommission
    ) {
        this.strategyStarter = strategyStarter;
        this.instrumentId = instrumentId;
        this.initialInvestment = initialInvestment;
        this.candleRepository = candleRepository;
        this.instrumentRepository = instrumentRepository;
        this.broker = broker;
        this.clock = clock;
        this.brokerCommission = brokerCommission;
    }

    public AnalysisReport run(Instant start, Instant end) throws AbstractException {
        StrategySimulationRunner strategyRunner = new StrategySimulationRunner(
            strategyStarter,
            broker,
            instrumentId,
            initialInvestment,
            clock
        );
        StrategyResult mainStrategyResult = strategyRunner.run(start, end);

        ConservativeStrategyRunner conservativeRunner = new ConservativeStrategyRunner(
            instrumentId,
            candleRepository,
            instrumentRepository,
            brokerCommission,
            initialInvestment
        );
        StrategyResult conservativeStrategyResult = conservativeRunner.run(start, end);

        double effectiveness = mainStrategyResult.apy() - conservativeStrategyResult.apy();

        return new AnalysisReport(
            mainStrategyResult,
            conservativeStrategyResult,
            effectiveness,
            mainStrategyResult.accountId()
        );
    }
}
