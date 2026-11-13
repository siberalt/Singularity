package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * Фиксация только вдали от средней, и тем больше, чем дальше ушла цена.
 * <pre>
 *     d = сторона · расстояние
 *     доля = d ≥ threshold ? share · clamp(d / fullDistance, 0, 1) : 0
 * </pre>
 * Ближе порога выход не снимает ничего и фиксацией не считается. Всего за позицию - не больше {@code cap}:
 * последняя фиксация урезается до остатка, и на нём план выполнен.
 * <p>
 * Расстояние - любая мера со знаком, плюс выше средней. Дневное расстояние полосы на часовой ленте, без
 * заглядывания вперёд:
 * <pre>
 *     new DistanceTakePlan(new ClosedBarsCoefficient(CandleInterval.DAY, 480, band::lastDistanceOf), 2.5, 0.2, 0.5, 5)
 * </pre>
 * {@code fullDistance} ноль - доля {@code share} на каждом выходе за порогом. Это контроль: сравнивать с ним
 * надо при той же средней доле, тогда разница - ровно то, что даёт пропорциональность.
 *
 * <h2>Почему порог</h2>
 * Без него план почти не выполняется. Над голой кривизной событие - каждый час встречной кривизны, и на 94%
 * таких часов цена у средней или за ней: доля ноль, план стоит. Фиксация {@code 0.2 · min(1, d / 5)} до 0.5
 * без порога проиграла шагам 0.1, 0.2, 0.2 на часовом стенде: −9.0 ± 6.0, - и проиграла не долей, а тем,
 * что до откупа дело не доходило.
 */
public class DistanceTakePlan implements TakePlan {
    private final MarketCoefficient distance;

    private final double threshold;

    private final double share;

    private final double cap;

    private final double fullDistance;

    /**
     * @param distance     расстояние цены от средней, со знаком; {@link Double#NaN} - снимается ноль
     * @param threshold    с какого расстояния в сторону позиции фиксировать, в единицах меры, не меньше нуля
     * @param share        доля счёта, которую снимает фиксация на полном расстоянии
     * @param cap          сколько всего можно снять за позицию, не меньше {@code share}
     * @param fullDistance с какого расстояния доля полная; ноль - не зависит от расстояния
     */
    public DistanceTakePlan(MarketCoefficient distance, double threshold, double share, double cap,
                            double fullDistance) {
        if (distance == null) {
            throw new IllegalArgumentException("Нужна мера расстояния от средней");
        }

        if (!(threshold >= 0) || Double.isInfinite(threshold)) {
            throw new IllegalArgumentException("Порог расстояния неотрицателен, получено " + threshold);
        }

        if (!(share > 0) || !(cap >= share) || cap > 1) {
            throw new IllegalArgumentException(
                "Нужно 0 < доля ≤ всего ≤ 1, получено %s и %s".formatted(share, cap));
        }

        if (!(fullDistance >= 0) || Double.isInfinite(fullDistance)) {
            throw new IllegalArgumentException("Расстояние полной доли неотрицательно, получено " + fullDistance);
        }

        this.distance = distance;
        this.threshold = threshold;
        this.share = share;
        this.cap = cap;
        this.fullDistance = fullDistance;
    }

    @Override
    public double shareOf(TakeProgress progress, List<Candle> lastCandles) {
        double along = progress.side() * distance.of(lastCandles);

        // Сравнение с NaN ложно: не измеренное расстояние порог не проходит.
        if (!(along >= threshold)) {
            return 0;
        }

        double fullness = fullDistance == 0 ? 1 : Math.clamp(along / fullDistance, 0, 1);

        return Math.clamp(share * fullness, 0, Math.max(0, cap - progress.taken()));
    }

    @Override
    public boolean done(TakeProgress progress) {
        return progress.taken() >= cap - 1e-12;
    }
}
