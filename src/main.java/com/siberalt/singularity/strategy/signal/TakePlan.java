package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Сколько снимает фиксация - контракт, по которому {@link PartialExitSignalSource} спрашивает долю тейка.
 * Фиксер решает, <b>когда</b> фиксировать: встречный выход после входа. План решает, <b>сколько</b>, и
 * когда хватит.
 * <p>
 * Состояние позиции держит фиксер и отдаёт его сюда: сколько фиксаций уже было и сколько они сняли. Поэтому
 * план сам ничего не помнит, а один и тот же план читается одинаково на любой позиции.
 *
 * @see StepTakePlan доли по шагам, названные заранее
 * @see DistanceTakePlan доля по расстоянию цены от средней
 */
public interface TakePlan {
    /**
     * Доля счёта, которую снимает фиксация на этом баре.
     *
     * @param steps       сколько фиксаций у позиции уже было
     * @param taken       сколько они сняли вместе, доля счёта
     * @param side        сторона позиции: 1 - лонг, −1 - шорт
     * @param lastCandles свечи бара, на котором фиксируется
     * @return доля всего счёта в [0, 1]; ноль - на этом сигнале не фиксировать, и фиксацией он не считается
     */
    double shareOf(int steps, double taken, double side, List<Candle> lastCandles);

    /**
     * Выполнен ли план - тогда фиксер снова ждёт вход. Спрашивается после каждой фиксации.
     *
     * @param steps сколько фиксаций у позиции было, считая эту
     * @param taken сколько они сняли вместе
     */
    boolean done(int steps, double taken);
}
