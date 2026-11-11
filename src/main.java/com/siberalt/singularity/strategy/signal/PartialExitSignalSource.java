package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Частичная фиксация поверх любого источника: пропускает его вход, а встречные выходы превращает в
 * фиксации, пока план не выполнен. Тогда снова ждёт вход - и так по новой.
 * <pre>
 *     new PartialExitSignalSource(new EntryExitSignalSource(slopeChange, slopeChange, 0.9), 0.9, 0.9, 0.1, 0.2, 0.2)
 * </pre>
 *
 * <h2>Два состояния</h2>
 * <ol>
 *     <li><b>Ждёт вход.</b> Сигнал источника, который открывает ({@link SignalType#opens()}) и по модулю не
 *     ниже порога входа, проходит как есть, и его сторона становится стороной позиции. Всё остальное -
 *     {@link Signal#NEUTRAL}.</li>
 *     <li><b>Ловит фиксации.</b> Сигнал против позиции, который закрывает ({@link SignalType#closes()}) и
 *     по модулю не ниже порога выхода, становится фиксацией: {@link SignalType#TAKE_PROFIT}, полная
 *     уверенность против позиции, доля - та, что назвал {@link TakePlan план}, сила - та, что измерил
 *     источник. Когда план выполнен - снова ожидание входа. Остальное - {@link Signal#NEUTRAL}, в том числе
 *     новые входы: пока план не выполнен, позиция одна.</li>
 * </ol>
 * {@link SignalType#UNSPECIFIED} годится в обе роли: источник, который знает только направление, не знает,
 * вход это или выход, а здесь это знают. Поэтому снизу может стоять и голый {@link SlopeChangeSignalSource}.
 * <p>
 * Сколько снимать - решает план: {@link StepTakePlan} - доли по шагам, {@link DistanceTakePlan} - по
 * расстоянию от средней. Доля - от всего счёта, а не от позиции и не от остатка: шаги 0.1, 0.2 и 0.2 снимают
 * половину счёта. Выход, на котором план назвал ноль, фиксацией не считается и проходит молча. Уверенность
 * фиксации полная, потому что решение принято здесь, по своему порогу, - а порог продажи стратегии должен
 * её пропустить.
 *
 * <h2>Чего фиксер не знает</h2>
 * Счёта. Смена тренда, полное закрытие и то, какие стороны вообще пускать, - решение слоя выше: он видит и
 * вход, и фиксации, и отсекает то, что ему не подходит. Вход, который счёт не исполнил - шорт без названной
 * доли, скажем, - фиксер всё равно считает позицией. А фиксация больше позиции позицию не перевернёт:
 * {@link com.siberalt.singularity.strategy.impl.quantity.TradeQuantity#namedLots}.
 * <p>
 * Источник с порогом ниже порога фиксера кормит его плохо. {@link EntryExitSignalSource} без своего порога
 * входит на первом баре после выхода, где кривизна только что сменила знак: на часовом стенде у таких входов
 * |c| медиана 0.066, и порог 0.9 проходят 5% - фиксер почти не включается. С тем же порогом у
 * {@code EntryExitSignalSource} входы и выходы - переходы через ±0.9, и фиксер работает.
 *
 * <h2>Замер</h2>
 * Часовой стенд по ликвидной трети: слой выше - вход на всю цель на смене дневного тренда и закрытие на
 * следующей, повторный вход на всю цель по входу фиксера в сторону тренда; план 0.1, 0.2, 0.2, пороги
 * 0.9. Лонг против скелета без фиксации: над голой кривизной SlopeChangeSignalSource(120) +9.6 ± 4.6, но
 * минус в одном периоде из четырёх; над EntryExit с порогом 0.9 +1.8 ± 3.2. С шортом голая кривизна
 * проходит правило, +17.6 ± 2.5, - но потому, что урезает убыточный шорт: лонга без шорта она не догоняет.
 *
 * <h2>Состояние</h2>
 * Помнит сторону позиции, число фиксаций и снятое. Один экземпляр - одна бумага и один прогон.
 */
public class PartialExitSignalSource implements SignalSource {
    private final SignalSource base;

    private final double entryThreshold;

    private final double exitThreshold;

    private final TakePlan plan;

    private double side = 0;

    private int steps = 0;

    private double taken = 0;

    /**
     * @param base           источник входов и выходов
     * @param entryThreshold с какой уверенности вход - вход, в (0, 1]
     * @param exitThreshold  с какой уверенности встречный выход - фиксация, в (0, 1]
     * @param plan           сколько снимает каждая фиксация и когда хватит
     */
    public PartialExitSignalSource(SignalSource base, double entryThreshold, double exitThreshold, TakePlan plan) {
        if (base == null) {
            throw new IllegalArgumentException("Нужен источник входов и выходов");
        }

        if (!(entryThreshold > 0) || entryThreshold > 1 || !(exitThreshold > 0) || exitThreshold > 1) {
            throw new IllegalArgumentException(
                "Пороги лежат в (0, 1], получено %s и %s".formatted(entryThreshold, exitThreshold));
        }

        if (plan == null) {
            throw new IllegalArgumentException("Нужен план: сколько снимает фиксация");
        }

        this.base = base;
        this.entryThreshold = entryThreshold;
        this.exitThreshold = exitThreshold;
        this.plan = plan;
    }

    /** План шагами - см. {@link StepTakePlan}. */
    public PartialExitSignalSource(SignalSource base, double entryThreshold, double exitThreshold, double... plan) {
        this(base, entryThreshold, exitThreshold, new StepTakePlan(plan));
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
            steps = 0;
            taken = 0;

            return signal;
        }

        boolean against = side * signal.confidence() < 0;
        boolean exit = signal.type().closes() || signal.type() == SignalType.UNSPECIFIED;

        if (!against || !exit || conviction < exitThreshold) {
            return Signal.NEUTRAL;
        }

        double share = plan.shareOf(steps, taken, side, lastCandles);

        if (!(share >= 0) || share > 1) {
            throw new IllegalStateException("План назвал долю вне [0, 1]: " + share);
        }

        if (share == 0) {
            return Signal.NEUTRAL;
        }

        Signal fixation = new Signal(SignalType.TAKE_PROFIT, -side, signal.strength(), share);

        steps++;
        taken += share;

        if (plan.done(steps, taken)) {
            side = 0;
        }

        return fixation;
    }
}
