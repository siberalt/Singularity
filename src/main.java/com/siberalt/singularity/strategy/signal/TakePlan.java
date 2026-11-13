package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Сколько снимает фиксация - контракт, по которому {@link PartialExitSignalSource} спрашивает долю тейка.
 * Фиксер решает, <b>когда</b> фиксировать: встречный выход базы. План решает, <b>сколько</b>, и когда хватит.
 * Когда откупать снятое, решает третий - {@link ReentryRule}.
 * <p>
 * Состояние позиции держит фиксер и отдаёт его сюда как {@link TakeProgress}. Поэтому план сам ничего не
 * помнит, а один и тот же план читается одинаково на любой позиции.
 *
 * @see StepTakePlan доли по шагам, названные заранее
 * @see DistanceTakePlan доля по расстоянию цены от средней
 */
public interface TakePlan {
    /**
     * Доля счёта, которую снимает фиксация на этом баре. Спрашивается, только пока план не выполнен.
     *
     * @param progress    где позиция в плане
     * @param lastCandles свечи бара, на котором фиксируется
     * @return доля всего счёта в [0, 1]; ноль - на этом сигнале не фиксировать, и фиксацией он не считается
     */
    double shareOf(TakeProgress progress, List<Candle> lastCandles);

    /** Выполнен ли план: больше не фиксировать, ждать откупа. */
    boolean done(TakeProgress progress);
}
