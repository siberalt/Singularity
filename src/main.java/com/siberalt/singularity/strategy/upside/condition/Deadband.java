package com.siberalt.singularity.strategy.upside.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.SignalCondition;
import com.siberalt.singularity.strategy.upside.Upside;

import java.util.List;
import java.util.function.Supplier;

/**
 * Мёртвая зона вокруг нуля: сигнал исполняется только если его сила дошла до порога.
 * <p>
 * Нужно это тем сигналам, у которых сигналом является сторона. У {@link
 * com.siberalt.singularity.strategy.upside.trend.MacdUpsideCalculator} и {@link
 * com.siberalt.singularity.strategy.upside.trend.MovingAverageCrossUpsideCalculator} ответ равен ±1, а вся
 * величина отдана в {@code strength}, поэтому порогами стратегии мёртвую зону выразить нечем: {@code
 * buyThreshold} видит только единицу. Между тем пока линия крутится возле нуля, её знак меняется от шума, и
 * каждая такая смена - пара сделок; в этом проекте уже измерено, что MACD по гистограмме делает 50 входов
 * против 21 у MACD по линии и платит за это 12 процентных пунктов издержек. Зона отсекает именно эти
 * пересечения - те, где линия ещё никуда не ушла.
 * <p>
 * <b>Порог в единицах силы делегата, а не в единицах индикатора.</b> У обоих трендовых калькуляторов сила
 * нормирована на цену, то есть это доля цены: 0.002 означает «линия отошла от нуля хотя бы на 0.2% цены».
 * Так порог переносится между бумагами разной стоимости. У другого делегата будут другие единицы, и порог
 * придётся задавать в них.
 * <p>
 * <b>Что именно отодвигается от нуля, решает делегат, а не зона.</b> Для {@code Source.LINE} это сама
 * линия, то есть отрыв быстрой средней от медленной; для {@code Source.HISTOGRAM} - зазор между линией и её
 * сигнальной. Если нужно, чтобы от нуля отошли оба, это два условия, а не одно: {@code
 * deadband.and(deadband)} над двумя калькуляторами такого не даст, потому что каждый видит только своё
 * значение - нужны два фильтра, вложенных друг в друга.
 */
public class Deadband implements SignalCondition {
    private final double minimumStrength;

    /**
     * @param minimumStrength ниже этой силы сторона сигнала считается шумом; в единицах силы делегата
     */
    public Deadband(double minimumStrength) {
        if (!(minimumStrength > 0)) {
            throw new IllegalArgumentException(
                "Мёртвая зона нулевой ширины ничего не отсекает, получено " + minimumStrength);
        }

        this.minimumStrength = minimumStrength;
    }

    @Override
    public boolean holds(List<Candle> lastCandles, Supplier<Upside> signal) {
        Upside upside = signal.get();

        // Своё молчание делегат выражает нулевой силой, и оно само себя отсекает - отдельной проверки на
        // NEUTRAL не нужно.
        return upside != null && Math.abs(upside.strength()) >= minimumStrength;
    }
}
