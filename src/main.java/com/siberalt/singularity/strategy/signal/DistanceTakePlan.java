package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * Доля растёт с расстоянием цены от средней в сторону позиции: чем дальше ушла цена, тем больше фиксация.
 * <pre>
 *     доля = share · clamp(d / fullDistance, 0, 1),   d = сторона · расстояние
 * </pre>
 * Цена у средней или за ней не снимает ничего, и такой выход фиксацией не считается. Всего за позицию - не
 * больше {@code cap}: последняя фиксация урезается до остатка, и на нём план выполнен.
 * <p>
 * Расстояние - любая мера со знаком, плюс выше средней. Дневное расстояние полосы на часовой ленте, без
 * заглядывания вперёд:
 * <pre>
 *     new DistanceTakePlan(new ClosedBarsCoefficient(CandleInterval.DAY, 480, band::lastDistanceOf), 0.12, 0.30, 5)
 * </pre>
 * {@code fullDistance} ноль - доля {@code share} на каждом выходе, расстояние не спрашивается. Это контроль:
 * сравнивать с ним надо при той же средней доле, тогда разница - ровно то, что даёт расстояние.
 *
 * <h2>Замер</h2>
 * Автомат с этим правилом на часовом стенде по ликвидной трети - кривизна SlopeChangeSignalSource(120),
 * расстояние дня в ATR(14) от SMA(80), 0.12 · min(1, d / 5), всего до 0.30, без повторного входа, - со
 * скелетом без фиксации не разошёлся (−0.5 ± 2.0), а постоянной доле той же средней проиграл: −2.5 ± 1.3.
 */
public class DistanceTakePlan implements TakePlan {
    private final MarketCoefficient distance;

    private final double share;

    private final double cap;

    private final double fullDistance;

    /**
     * @param distance     расстояние цены от средней, со знаком; {@link Double#NaN} - снимается ноль
     * @param share        доля счёта, которую снимает фиксация на полном расстоянии
     * @param cap          сколько всего можно снять за позицию, не меньше {@code share}
     * @param fullDistance с какого расстояния доля полная, в единицах меры; ноль - не зависит от расстояния
     */
    public DistanceTakePlan(MarketCoefficient distance, double share, double cap, double fullDistance) {
        if (distance == null) {
            throw new IllegalArgumentException("Нужна мера расстояния от средней");
        }

        if (!(share > 0) || !(cap >= share) || cap > 1) {
            throw new IllegalArgumentException(
                "Нужно 0 < доля ≤ всего ≤ 1, получено %s и %s".formatted(share, cap));
        }

        if (!(fullDistance >= 0) || Double.isInfinite(fullDistance)) {
            throw new IllegalArgumentException("Расстояние полной доли неотрицательно, получено " + fullDistance);
        }

        this.distance = distance;
        this.share = share;
        this.cap = cap;
        this.fullDistance = fullDistance;
    }

    @Override
    public double shareOf(int steps, double taken, double side, List<Candle> lastCandles) {
        return Math.clamp(share * fullness(side, lastCandles), 0, Math.max(0, cap - taken));
    }

    @Override
    public boolean done(int steps, double taken) {
        return taken >= cap - 1e-12;
    }

    /** Доля от полной: у средней ноль, с {@code fullDistance} в сторону позиции - единица. */
    private double fullness(double side, List<Candle> lastCandles) {
        if (fullDistance == 0) {
            return 1;
        }

        double along = side * distance.of(lastCandles);

        return Double.isNaN(along) ? 0 : Math.clamp(along / fullDistance, 0, 1);
    }
}
