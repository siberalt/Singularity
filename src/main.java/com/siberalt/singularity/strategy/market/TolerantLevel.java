package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.signal.ChangeAngleSignalSource;
import com.siberalt.singularity.strategy.signal.Signal;

import java.util.List;

/**
 * Уровень, опущенный на столько, насколько ход через него нерешителен: резкому падению терпимости нет,
 * дрейфу дана зона.
 * <p>
 * Смысл правила такой. Пробой уровня бывает двух сортов: цена может провалиться сквозь него и уйти, а
 * может сползать через него неделю, ничего этим не сообщая. Второе стоит переждать, и «переждать» означает
 * сдвинуть порог выхода ниже - в зону поддержки под уровнем. Первое переждать нельзя.
 * <p>
 * <b>Это один уровень вместо двух условий.</b> До появления этого коэффициента правило требовало «цена под
 * уровнем <i>и</i> падает круто», то есть два условия и порог резкости отдельно от уровня. Здесь резкость
 * входит в сам уровень, и проверка снова одна - {@link PriceAbove} над этой формулой. Заодно исчезает
 * дырка той конструкции: медленный дрейф, ушедший на десять процентов вниз, не выходил никогда, потому что
 * круто он так и не падал.
 *
 * <h2>Терпимость либо есть, либо нет - и это осознанно</h2>
 * Порог резкости делит ход на два сорта, а не задаёт плавную зависимость. Плавная версия (терпимость,
 * убывающая с резкостью линейно) стоит столько же параметров, но добавляет третье предположение - про
 * линейность, - и проверять надо, добавляет ли оно что-нибудь. Пока не проверено, форма берётся с меньшим
 * числом предположений.
 *
 * <h2>Участие в ходе</h2>
 * {@code minParticipation} снимает терпимость только с тех резких падений, за которыми стоит объём. По
 * умолчанию ноль, то есть объём не спрашивается и поведение определяется одной резкостью.
 * <p>
 * Параметр существует потому, что резкость и значимость - разные вещи, и это измерено: самый резкий пробой
 * в этой базе (Сбер, суббота 24.06.2023, минус 7.2% за 84 минуты) был ходом выходной площадки с процентом
 * будничной ликвидности и не подтвердился никогда - в понедельник открылись выше пятничного закрытия.
 * Участие и отличает такой ход от настоящего, а {@link ChangeAngleSignalSource} отдаёт угол объёма в
 * {@link Signal#strength()}: единица - объём рос на каждом баре, половина - стоял.
 */
public class TolerantLevel implements MarketCoefficient {
    private final MarketCoefficient level;

    private final ChangeAngleSignalSource move;

    private final double maxTolerance;

    private final double steepEnough;

    private final double minParticipation;

    /**
     * @param level            уровень, от которого отсчитывается зона
     * @param move             чем мерить решительность хода; угол нормирован и не выходит за предел
     * @param maxTolerance     насколько опускать уровень на нерешительном ходу, долей цены в (0, 1)
     * @param steepEnough      с какой доли предельного угла терпимости больше нет, в (0, 1]
     * @param minParticipation какой угол объёма нужен, чтобы снять терпимость; ноль - не спрашивать
     */
    public TolerantLevel(MarketCoefficient level, ChangeAngleSignalSource move, double maxTolerance,
                         double steepEnough, double minParticipation) {
        if (level == null || move == null) {
            throw new IllegalArgumentException("Нужны уровень и чем мерить ход через него");
        }

        if (!(maxTolerance > 0) || maxTolerance >= 1) {
            throw new IllegalArgumentException(
                "Терпимость - доля цены в (0, 1), получено " + maxTolerance);
        }

        if (!(steepEnough > 0) || steepEnough > 1) {
            throw new IllegalArgumentException(
                "Порог резкости - доля предельного угла в (0, 1], получено " + steepEnough);
        }

        if (minParticipation < 0 || minParticipation > 1) {
            throw new IllegalArgumentException(
                "Угол объёма лежит в [0, 1], получено " + minParticipation);
        }

        this.level = level;
        this.move = move;
        this.maxTolerance = maxTolerance;
        this.steepEnough = steepEnough;
        this.minParticipation = minParticipation;
    }

    /** Без требования к объёму: решительность определяется одной резкостью хода. */
    public TolerantLevel(MarketCoefficient level, ChangeAngleSignalSource move, double maxTolerance,
                         double steepEnough) {
        this(level, move, maxTolerance, steepEnough, 0);
    }

    @Override
    public double of(List<Candle> lastCandles) {
        double base = level.of(lastCandles);

        if (Double.isNaN(base)) {
            return Double.NaN;
        }

        Signal reading = move.calculate(lastCandles);
        // Минус уверенности - доля предельного угла, пройденная вниз; рост терпимости не отменяет.
        double fall = Math.max(0, -reading.confidence());
        boolean decisive = fall >= steepEnough && reading.strength() >= minParticipation;

        return decisive ? base : base * (1 - maxTolerance);
    }
}
