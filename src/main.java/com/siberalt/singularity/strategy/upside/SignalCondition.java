package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCondition;

import java.util.List;
import java.util.function.Supplier;

/**
 * Условие, при котором сигнал стоит исполнять, с правом заглянуть в сам сигнал - но только если нужно.
 * <p>
 * Старший брат {@link MarketCondition}, и отличие ровно одно: сигнал приходит {@link Supplier}, а не
 * значением. Так условию, которому сигнал не нужен - тихая сессия, час дня, инструмент перестал печатать, -
 * ничего не стоит отказать, не вызвав делегата вовсе; а условию, которому сигнал нужен, он доступен.
 * Передавать готовый {@link Upside} было бы проще, но тогда делегата пришлось бы спрашивать на каждом баре,
 * включая те, где ответ всё равно не понадобится, а у делегатов в этом проекте расчёт не бесплатный.
 * <p>
 * Что гарантирует {@link FilterUpsideCalculator} про этот поставщик:
 * <ul>
 *   <li><b>Делегата спросят не больше одного раза за бар.</b> Сколько бы раз условие ни дёрнуло
 *   {@code get()}, считается один вызов, и его же ответ уходит наружу, если условие прошло. Значит условию
 *   не нужно кэшировать сигнал самому.</li>
 *   <li><b>Но спросить - значит вызвать.</b> Если условие заглянуло в сигнал и после этого отказало,
 *   делегат на этом баре уже отработал. Для делегата, который считает бары
 *   ({@link FixedSignalReverserUpsideCalculator}, {@link EntryExitUpsideCalculator}), это означает, что его
 *   счётчик сдвинулся, а наружу ничего не вышло. Условию, которому сигнал не нужен, лучше его и не
 *   спрашивать.</li>
 * </ul>
 * Что здесь уместно, а что нет. Уместно то, чего пороги стратегии выразить не могут: мёртвая зона у сигнала,
 * который сам по себе равен только ±1 ({@link com.siberalt.singularity.strategy.upside.condition.Deadband}),
 * или условие, смысл которого зависит от стороны - покупать на откате, продавать на отскоке
 * ({@link com.siberalt.singularity.strategy.upside.condition.RsiSide}). Неуместно повторять порог, который
 * у стратегии уже есть: {@code buyThreshold} и {@code sellThreshold} читают тот же сигнал, и второе место,
 * где написано то же самое, рано или поздно разойдётся с первым.
 */
@FunctionalInterface
public interface SignalCondition {
    /**
     * @param lastCandles те же свечи, что видит сигнал
     * @param signal      сам сигнал, если он условию нужен
     */
    boolean holds(List<Candle> lastCandles, Supplier<Upside> signal);

    /** Условие о рынке, которому сигнал не нужен вовсе, - то есть делегата оно не разбудит. */
    static SignalCondition of(MarketCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("Нет условия, которое надо обернуть");
        }

        return (lastCandles, signal) -> condition.holds(lastCandles);
    }

    /** Оба условия, причём второе не спрашивают, если первое уже отказало. */
    default SignalCondition and(SignalCondition other) {
        if (other == null) {
            throw new IllegalArgumentException("Нет второго условия");
        }

        return (lastCandles, signal) -> holds(lastCandles, signal) && other.holds(lastCandles, signal);
    }

    /**
     * То же условие, но спрашиваемое только про покупку: продажа проходит, ни о чём не спрашивая.
     * <p>
     * Нужно это потому, что в правиле, где один калькулятор и открывает, и закрывает позицию, фильтр на
     * обе стороны задерживает закрытие - а в этом проекте уже измерено, сколько такая задержка стоит: 51
     * пункт у RSI-фильтра и 27 пунктов на одной сделке у мёртвой зоны, попавшей на 2022-09-21. Поэтому
     * «фильтруй то, что открывает сделку, и не трогай то, что закрывает» - это не оговорка, а рабочая
     * форма почти любого условия здесь.
     * <p>
     * Молчание делегата проходит так же, как продажа: фильтровать нечего, наружу всё равно уйдёт
     * {@link Upside#NEUTRAL}.
     */
    default SignalCondition onlyForBuys() {
        return onlyFor(1);
    }

    /** То же условие, но спрашиваемое только про продажу. Зеркало {@link #onlyForBuys()}. */
    default SignalCondition onlyForSells() {
        return onlyFor(-1);
    }

    /**
     * Условие, заданное одной стороне сигнала.
     * <p>
     * Сторону приходится узнать, то есть делегата это разбудит даже там, где условие в итоге ничего не
     * решает. Зато дальше условию отдаётся уже готовый ответ, а не новый вызов, - иначе один бар считался
     * бы дважды.
     */
    private SignalCondition onlyFor(double side) {
        return (lastCandles, signal) -> {
            Upside upside = signal.get();

            if (upside == null || upside.signal() * side <= 0) {
                return true;
            }

            return holds(lastCandles, () -> upside);
        };
    }
}
