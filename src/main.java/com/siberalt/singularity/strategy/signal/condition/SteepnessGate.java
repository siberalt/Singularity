package com.siberalt.singularity.strategy.signal.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalCondition;

import java.util.List;
import java.util.function.Supplier;

/**
 * Не покупать на крутых отрезках: сигнал на покупку не проходит, когда крутизна выше порога; продажи и
 * молчание проходят всегда. Условие для {@link com.siberalt.singularity.strategy.signal.FilterSignalSource}.
 * <pre>
 *     SlopeChangeSignalSource curvature = new SlopeChangeSignalSource(StretchPolicy.betweenPivots(new ZigZagExtremeLocator(6)))
 *         .setFormula(SlopeChangeFormula.MIRROR);
 *     SignalSource gentle = new FilterSignalSource(
 *         new EntryExitSignalSource(curvature, exit),
 *         new SteepnessGate(new SlopeSteepness(curvature), 0.133));
 * </pre>
 * Покупка - любой сигнал с уверенностью выше нуля, какого бы типа он ни был: стратегия тип не читает, и
 * закрытие шорта, которого у счёта только лонга нет, для неё - тоже покупка. Продажу не трогать нарочно:
 * фильтровать надо то, что открывает сделку, а то, что её закрывает, - нет, иначе позиции нечем будет
 * закончиться.
 * <p>
 * Сигнал спрашивается на каждом баре, в том числе на отказанном: так источник со счётом баров - {@link
 * com.siberalt.singularity.strategy.signal.EntryExitSignalSource} - видит каждый бар. Крутизна - только там,
 * где сигнал покупает. Не измерить ({@link Double#NaN}) - не круто: покупка проходит.
 *
 * <h2>Замер</h2>
 * Часовой стенд, createLevelsStrategy с MIRROR: кривизна между разворотами ZigZag(6) по 300 часам,
 * EntryExit, пороги ±0.9, крутизна в ATR(14) по тем же часам. Порог 0.133 - медиана крутизны на всех часах
 * ликвидной трети до 2023-07, без сделок. Он отсекает 60% входов. Ликвидная треть, 12.5 бп за сторону: к
 * MIRROR без фильтра +40.9 ± 13.2, к фонду +31.7 ± 12.9 и плюс во всех четырёх периодах; при квартилях
 * 0.088 и 0.212 к MIRROR +29.0 и +15.1. Но в сильном росте 2023-24 фильтр MIRROR уступает: −20.2. На 24
 * бумагах вне ликвидной трети фильтр лучше MIRROR у 18 из 23, но средняя −2.0 ± 30.4: на одной бумаге он
 * пропустил ралли, на котором MIRROR дал +697 к фонду. Фонд вне ликвидной трети не обгоняет: −3.8 ± 9.4
 * при 12.5 бп. Фильтр улучшает кривизну, но платит за это редкими крутыми ралли.
 */
public class SteepnessGate implements SignalCondition {
    private final MarketCoefficient steepness;

    private final double maxSteepness;

    /**
     * @param steepness    крутизна отрезков, обычно {@link com.siberalt.singularity.strategy.signal.SlopeSteepness}
     * @param maxSteepness с какой крутизны не покупать, в её единицах; больше нуля
     */
    public SteepnessGate(MarketCoefficient steepness, double maxSteepness) {
        if (steepness == null) {
            throw new IllegalArgumentException("Нужна мера крутизны");
        }

        if (!(maxSteepness > 0) || Double.isInfinite(maxSteepness)) {
            throw new IllegalArgumentException("Порог крутизны положителен, получено " + maxSteepness);
        }

        this.steepness = steepness;
        this.maxSteepness = maxSteepness;
    }

    @Override
    public boolean holds(List<Candle> lastCandles, Supplier<Signal> signal) {
        if (signal.get().confidence() <= 0) {
            return true;
        }

        // Сравнение с NaN ложно: не измеренная крутизна покупку не останавливает.
        return !(steepness.of(lastCandles) > maxSteepness);
    }
}
