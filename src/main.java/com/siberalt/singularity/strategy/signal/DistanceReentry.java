package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * Откуп по расстоянию цены от средней в сторону позиции, {@code d = сторона · расстояние}:
 * <ul>
 *     <li><b>пока план не выполнен</b> - на откате: {@code lower ≤ d ≤ pullback}. Проданное вдали от
 *     средней откупается ближе к ней, то есть дешевле;</li>
 *     <li><b>когда выполнен</b> - и без отката: {@code d ≥ lower}. Это запасной вход: в сильном тренде цена к
 *     средней не возвращается, и без него проданное так и лежало бы в деньгах до смены тренда.</li>
 * </ul>
 * Ниже {@code lower} не откупается никогда: у самой средней тренд скорее сменится, чем продолжится.
 * <p>
 * Почему запасной вход нужен, измерено: фиксация без откупа на часовом стенде с постоянной долей не
 * разошлась (−2.5 ± 1.3 у расстояния против неё), а после фиксаций цена в среднем шла по тренду ещё
 * +2.1 ± 1.1% - при медиане −1.8%, то есть держат это редкие длинные продолжения.
 */
public class DistanceReentry implements ReentryRule {
    private final MarketCoefficient distance;

    private final double lower;

    private final double pullback;

    /**
     * @param distance расстояние цены от средней, со знаком; {@link Double#NaN} - откупа нет
     * @param lower    ближе этого к средней не откупать, не меньше нуля
     * @param pullback до какого расстояния откат считается откатом, не меньше {@code lower}
     */
    public DistanceReentry(MarketCoefficient distance, double lower, double pullback) {
        if (distance == null) {
            throw new IllegalArgumentException("Нужна мера расстояния от средней");
        }

        if (!(lower >= 0) || !(pullback >= lower) || Double.isInfinite(pullback)) {
            throw new IllegalArgumentException(
                "Нужно 0 ≤ нижний ≤ откат, получено %s и %s".formatted(lower, pullback));
        }

        this.distance = distance;
        this.lower = lower;
        this.pullback = pullback;
    }

    @Override
    public boolean allows(TakeProgress progress, boolean done, List<Candle> lastCandles) {
        double along = progress.side() * distance.of(lastCandles);

        // Сравнение с NaN ложно: не измеренное расстояние откупа не даёт.
        return along >= lower && (done || along <= pullback);
    }
}
