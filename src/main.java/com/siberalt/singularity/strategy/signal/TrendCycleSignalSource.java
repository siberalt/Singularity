package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * {@link TrendCycle} в стратегии: тренд и наклон читаются с ленты, а наружу уходит цель автомата как
 * названная доля счёта - для {@link com.siberalt.singularity.strategy.impl.quantity.TargetPositionQuantity}.
 *
 * <h2>Уверенность - сторона последнего изменения цели</h2>
 * Стратегия выбирает сторону сделки по уверенности, а цель - это позиция. Поэтому уверенность здесь
 * {@code +1}, если цель последний раз выросла, и {@code -1}, если упала, и держится она до следующего
 * изменения, а не один бар. Позиция набирается не за один бар, когда ликвидности мало; повтор той же
 * стороны доводит её до цели, а когда цель достигнута, разность до неё просит ноль. До первого изменения
 * сказать нечего - {@link Signal#NEUTRAL}.
 *
 * <h2>Окна - у делегатов</h2>
 * Источник отдаёт делегатам ровно то, что получил, и своего окна не держит: стратегия после каждого
 * расчёта начинает копить свечи заново. Тренд по закрытым дням держит свои бары сам -
 * {@link com.siberalt.singularity.strategy.market.ClosedBarsCoefficient}, - а наклону нужно окно, то есть
 * {@link WindowSignalSource}. Читается из сигнала наклона его уверенность; нейтральный - нулевой наклон,
 * и по порогу выше нуля он не входит.
 */
public class TrendCycleSignalSource implements SignalSource {
    private final MarketCoefficient trend;

    private final SignalSource slope;

    private final TrendCycle cycle;

    private double direction = 0;

    private SignalType type = SignalType.NONE;

    /**
     * @param trend тренд: {@link com.siberalt.singularity.strategy.market.TrendZone#UP},
     *              {@link com.siberalt.singularity.strategy.market.TrendZone#DOWN} или {@link Double#NaN}
     * @param slope наклон ноги, со своим окном
     * @param cycle автомат, новый на каждый прогон
     */
    public TrendCycleSignalSource(MarketCoefficient trend, SignalSource slope, TrendCycle cycle) {
        if (trend == null || slope == null || cycle == null) {
            throw new IllegalArgumentException("Нужны тренд, наклон и автомат");
        }

        this.trend = trend;
        this.slope = slope;
        this.cycle = cycle;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        double before = cycle.target();
        double leg = slope.calculate(lastCandles).confidence();
        double after = cycle.next(trend.of(lastCandles), leg);

        if (after != before) {
            direction = Math.signum(after - before);
            type = opens(before, after) ? SignalType.POSITION_ENTRY : SignalType.POSITION_EXIT;
        }

        return direction == 0 ? Signal.NEUTRAL : new Signal(type, direction, leg, after);
    }

    /** Вход - когда позиции стало больше или она сменила сторону; выход - когда её стало меньше. */
    private static boolean opens(double before, double after) {
        return Math.abs(after) > Math.abs(before) || after != 0 && Math.signum(after) != Math.signum(before);
    }
}
