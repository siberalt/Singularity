package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.signal.SmoothSwitchSignalSource;

import java.util.List;

/**
 * Сторона рынка, которая не дёргается: {@link #UP}, {@link #DOWN} или {@link #FLAT} по чтению любого
 * коэффициента, с двумя порогами вместо одного.
 * <p>
 * Объявить сторону стоит дорого - нужно решительное чтение, {@code decisive}. Удержать её стоит дешевле -
 * хватает согласного чтения в {@code remains}. Между этими двумя числами прежняя сторона остаётся, и
 * именно это гасит шум: небольшой изгиб против тренда сменой тренда не считается.
 *
 * <h2>Зачем два порога, а не один</h2>
 * Один порог даёт дребезг, и он уже измерен не здесь: в {@link SmoothSwitchSignalSource} записано, что
 * коэффициент, висящий около границы, «переворачивает ответ между одним баром и следующим», и стоит это
 * «не худшего чтения рынка, а churn - позиций, открытых и закрытых на шуме коэффициента». Значимость
 * изгиба меру направления от этого не спасает: порог остаётся один, висеть около него чтение может точно
 * так же.
 *
 * <h2>Это не мёртвая зона на сигнале</h2>
 * Мёртвая зона у нуля <b>на сигнале</b> оказалась в этой базе чистым лагом - -30, -59, -77, -112 пп,
 * монотонно по ширине, - потому что стояла на сигнале, которым было само пересечение нуля, и запрещала
 * ровно то событие, ради которого сигнал существует. Здесь порог стоит на <b>классификации режима</b>,
 * а не на спусковом крючке входа: задержка смены режима - не задержка входа. Гейтом на вход это ставить
 * нельзя, иначе получится ровно тот измеренный лаг.
 *
 * <h2>Контроль без гистерезиса - та же сборка</h2>
 * При {@code remains == decisive} два порога сходятся в один и получается обычное переключение. Это
 * нарочно: сравнивать гистерезис надо с ним самим в одной настройке, а не с другой конструкцией.
 *
 * <h2>Состояние</h2>
 * Экземпляр следит за одним рядом и для другого не переиспользуется: сторона помнится между вызовами, и
 * ответ зависит от того, что спрашивали раньше. Пока чтения нет - {@link Double#NaN}, - сторона не
 * меняется: отсутствие данных это не повод объявить флэт.
 */
public class SteadySide implements MarketCoefficient {
    public static final double UP = 1;

    public static final double DOWN = -1;

    public static final double FLAT = 0;

    private final MarketCoefficient reading;

    private final double decisive;

    private final double remains;

    private double side = FLAT;

    /**
     * @param reading  чем читать сторону; ожидается нормированная мера, скажем {@link TrendAngle}
     * @param decisive какое чтение объявляет сторону, доля предела в (0, 1]
     * @param remains  при каком согласном чтении объявленная сторона остаётся, в [0, decisive]
     */
    public SteadySide(MarketCoefficient reading, double decisive, double remains) {
        if (reading == null) {
            throw new IllegalArgumentException("Нужно, чем читать сторону");
        }

        if (!(decisive > 0) || decisive > 1) {
            throw new IllegalArgumentException(
                "Порог объявления - доля предела в (0, 1], получено " + decisive);
        }

        if (remains < 0 || remains > decisive) {
            throw new IllegalArgumentException(
                "Порог удержания лежит в [0, " + decisive + "], получено " + remains);
        }

        this.reading = reading;
        this.decisive = decisive;
        this.remains = remains;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        double value = reading.of(lastCandles);

        if (Double.isNaN(value)) {
            return Double.NaN;
        }

        // Согласное чтение держит сторону; несогласное её снимает, но назначить обратную может только
        // решительное - слабый изгиб против тренда это не другой тренд, это отсутствие тренда.
        if (side != FLAT && side * value >= remains) {
            return side;
        }

        side = Math.abs(value) >= decisive ? Math.signum(value) : FLAT;

        return side;
    }

    /** Сторона, объявленная последним чтением, без нового вопроса к коэффициенту. */
    public double side() {
        return side;
    }
}
