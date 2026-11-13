package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Можно ли откупить снятое - контракт, по которому {@link PartialExitSignalSource} решает, что сигнал базы
 * по стороне позиции - откуп. Сколько откупать, правило не решает: ровно снятое.
 * <p>
 * Спрашивается, только когда что-то снято и база сказала «по позиции» не слабее порога входа.
 *
 * @see DistanceReentry откуп на откате к средней, а после плана - и без отката
 */
@FunctionalInterface
public interface ReentryRule {
    /**
     * @param progress    где позиция в плане
     * @param done        выполнен ли план - спросил фиксер у {@link TakePlan#done}
     * @param lastCandles свечи бара
     */
    boolean allows(TakeProgress progress, boolean done, List<Candle> lastCandles);

    /** Откуп только после выполненного плана - фиксер до правила откупа вёл себя так. */
    static ReentryRule afterPlan() {
        return (progress, done, lastCandles) -> done;
    }
}
