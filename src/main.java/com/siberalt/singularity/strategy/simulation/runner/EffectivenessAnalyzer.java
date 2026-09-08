package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.simulation.SimulationBroker;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;

import java.time.Instant;

/**
 * Runs a strategy over a period and asks whether it was worth running: what it earned against what
 * simply holding the instrument would have earned over the same period, on the same terms.
 * <p>
 * On the same terms is the point. Both runs get their broker from {@code brokerFactory}, so the
 * benchmark pays the same commission, spread, impact and latency the strategy does. Measuring a
 * strategy that crosses the spread thousands of times against a buy-and-hold that fills perfectly
 * is not a comparison - it is a handicap, and it flatters every strategy that trades rarely.
 *
 * @param <B> the kind of broker the strategy under test needs
 */
public class EffectivenessAnalyzer<B extends SimulationBroker> {
    private final StrategyStarter<B> strategyStarter;
    private final String instrumentId;
    private final Money initialInvestment;
    private final ReadCandleRepository candleRepository;
    private final SimulationBrokerFactory<B> brokerFactory;
    private final OrderRepository orderRepository;
    private final OperationRepository operationRepository;

    /**
     * @param brokerFactory      how a broker for one run is built - used for the strategy's own and
     *                           for the benchmark's, which is what makes the two comparable
     * @param orderRepository    where the strategy's run writes its orders, passed in rather than
     *                           made here so the caller can read the run back afterwards
     * @param operationRepository likewise for its operations
     */
    public EffectivenessAnalyzer(
        StrategyStarter<B> strategyStarter,
        String instrumentId,
        Money initialInvestment,
        ReadCandleRepository candleRepository,
        SimulationBrokerFactory<B> brokerFactory,
        OrderRepository orderRepository,
        OperationRepository operationRepository
    ) {
        this.strategyStarter = strategyStarter;
        this.instrumentId = instrumentId;
        this.initialInvestment = initialInvestment;
        this.candleRepository = candleRepository;
        this.brokerFactory = brokerFactory;
        this.orderRepository = orderRepository;
        this.operationRepository = operationRepository;
    }

    public AnalysisReport run(Instant start, Instant end) throws AbstractException {
        SimulationClock clock = new SimpleSimulationClock();
        B broker = brokerFactory.create(clock, orderRepository, operationRepository);

        StrategyBacktester<B> backtester = new StrategyBacktester<>(
            strategyStarter,
            broker,
            instrumentId,
            initialInvestment,
            clock
        );
        StrategyResult mainStrategyResult = backtester.run(start, end);

        ConservativeStrategyRunner conservativeRunner = new ConservativeStrategyRunner(
            instrumentId,
            candleRepository,
            brokerFactory,
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
