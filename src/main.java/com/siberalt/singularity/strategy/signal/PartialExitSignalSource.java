package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Частичная фиксация поверх любого источника: пропускает его вход, а встречные выходы превращает в
 * фиксации по плану, шаг за шагом. Когда план выполнен, снова ждёт вход - и так по новой.
 * <pre>
 *     new PartialExitSignalSource(new EntryExitSignalSource(slopeChange, exit), 0.9, 0.9, 0.1, 0.2, 0.2)
 * </pre>
 *
 * <h2>Два состояния</h2>
 * <ol>
 *     <li><b>Ждёт вход.</b> Сигнал источника, который открывает ({@link SignalType#opens()}) и по модулю не
 *     ниже порога входа, проходит как есть, и его сторона становится стороной позиции. Всё остальное -
 *     {@link Signal#NEUTRAL}.</li>
 *     <li><b>Ловит фиксации.</b> Сигнал против позиции, который закрывает ({@link SignalType#closes()}) и
 *     по модулю не ниже порога выхода, становится фиксацией: {@link SignalType#TAKE_PROFIT}, полная
 *     уверенность против позиции, доля - следующий шаг плана, сила - та, что измерил источник. После
 *     последнего шага - снова ожидание входа. Остальное - {@link Signal#NEUTRAL}, в том числе новые входы:
 *     пока план не выполнен, позиция одна.</li>
 * </ol>
 * {@link SignalType#UNSPECIFIED} годится в обе роли: источник, который знает только направление, не знает,
 * вход это или выход, а здесь это знают. Поэтому снизу может стоять и голый {@link SlopeChangeSignalSource}.
 * <p>
 * Доля шага - от всего счёта, а не от позиции и не от остатка: шаги 0.1, 0.2 и 0.2 снимают половину
 * счёта. Уверенность фиксации полная, потому что решение принято здесь, по своему порогу, - а порог продажи
 * стратегии должен её пропустить.
 *
 * <h2>Чего фиксер не знает</h2>
 * Счёта. Смена тренда, полное закрытие и то, какие стороны вообще пускать, - решение слоя выше: он видит и
 * вход, и фиксации, и отсекает то, что ему не подходит. Вход, который счёт не исполнил - шорт без названной
 * доли, скажем, - фиксер всё равно считает позицией. А фиксация больше позиции позицию не перевернёт:
 * {@link com.siberalt.singularity.strategy.impl.quantity.TradeQuantity#namedLots}.
 *
 * <h2>Состояние</h2>
 * Помнит сторону позиции и номер шага. Один экземпляр - одна бумага и один прогон.
 */
public class PartialExitSignalSource implements SignalSource {
    private final SignalSource base;

    private final double entryThreshold;

    private final double exitThreshold;

    private final double[] plan;

    private double side = 0;

    private int step = 0;

    /**
     * @param base           источник входов и выходов
     * @param entryThreshold с какой уверенности вход - вход, в (0, 1]
     * @param exitThreshold  с какой уверенности встречный выход - фиксация, в (0, 1]
     * @param plan           доли счёта, которые снимает каждая следующая фиксация; вместе не больше счёта
     */
    public PartialExitSignalSource(SignalSource base, double entryThreshold, double exitThreshold, double... plan) {
        if (base == null) {
            throw new IllegalArgumentException("Нужен источник входов и выходов");
        }

        if (!(entryThreshold > 0) || entryThreshold > 1 || !(exitThreshold > 0) || exitThreshold > 1) {
            throw new IllegalArgumentException(
                "Пороги лежат в (0, 1], получено %s и %s".formatted(entryThreshold, exitThreshold));
        }

        if (plan == null || plan.length == 0) {
            throw new IllegalArgumentException("Нужен план: хотя бы одна фиксация");
        }

        for (double share : plan) {
            if (!(share > 0) || share > 1) {
                throw new IllegalArgumentException("Шаг плана - доля счёта в (0, 1], получено " + share);
            }
        }

        if (Arrays.stream(plan).sum() > 1 + 1e-9) {
            throw new IllegalArgumentException("Весь план больше счёта: " + Arrays.toString(plan));
        }

        this.base = base;
        this.entryThreshold = entryThreshold;
        this.exitThreshold = exitThreshold;
        this.plan = plan.clone();
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        Signal signal = base.calculate(lastCandles);
        double conviction = Math.abs(signal.confidence());

        if (side == 0) {
            boolean entry = signal.type().opens() || signal.type() == SignalType.UNSPECIFIED;

            if (!entry || conviction < entryThreshold) {
                return Signal.NEUTRAL;
            }

            side = Math.signum(signal.confidence());
            step = 0;

            return signal;
        }

        boolean against = side * signal.confidence() < 0;
        boolean exit = signal.type().closes() || signal.type() == SignalType.UNSPECIFIED;

        if (!against || !exit || conviction < exitThreshold) {
            return Signal.NEUTRAL;
        }

        Signal fixation = new Signal(SignalType.TAKE_PROFIT, -side, signal.strength(), plan[step++]);

        if (step == plan.length) {
            side = 0;
        }

        return fixation;
    }
}
