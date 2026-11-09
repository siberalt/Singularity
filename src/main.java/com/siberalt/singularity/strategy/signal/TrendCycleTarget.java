package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * {@link TrendCycle} на ленте: читает тренд и наклон и отдаёт автомату, а его цель - наружу. Сигналом её
 * делает {@link TargetSignalSource}:
 * <pre>
 *     new TargetSignalSource(new TrendCycleTarget(trend, slope, cycle))
 * </pre>
 * Решает автомат, здесь только чтения. Поэтому стенд, который подаёт автомату готовые ряды, и стратегия
 * расходятся только в том, как чтения получены, а не в правилах.
 *
 * <h2>Окна - у чтений</h2>
 * Цель отдаёт чтениям ровно то, что получила, и своего окна не держит: стратегия после каждого расчёта
 * начинает копить свечи заново. Тренд по закрытым дням держит свои бары сам -
 * {@link com.siberalt.singularity.strategy.market.ClosedBarsCoefficient}, - а наклону нужно окно, то есть
 * {@link WindowSignalSource}. Читается из сигнала наклона его уверенность; нейтральный - нулевой наклон,
 * и по порогу выше нуля он не входит.
 */
public class TrendCycleTarget implements PositionTarget {
    private final MarketCoefficient trend;

    private final SignalSource slope;

    private final TrendCycle cycle;

    /**
     * @param trend тренд: {@link com.siberalt.singularity.strategy.market.TrendZone#UP},
     *              {@link com.siberalt.singularity.strategy.market.TrendZone#DOWN} или {@link Double#NaN}
     * @param slope наклон ноги, со своим окном
     * @param cycle автомат, новый на каждый прогон
     */
    public TrendCycleTarget(MarketCoefficient trend, SignalSource slope, TrendCycle cycle) {
        if (trend == null || slope == null || cycle == null) {
            throw new IllegalArgumentException("Нужны тренд, наклон и автомат");
        }

        this.trend = trend;
        this.slope = slope;
        this.cycle = cycle;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        return cycle.next(trend.of(lastCandles), slope.calculate(lastCandles).confidence());
    }
}
