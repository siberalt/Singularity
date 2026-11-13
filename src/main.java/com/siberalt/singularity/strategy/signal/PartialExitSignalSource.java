package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Частичная фиксация одной стороны поверх любого источника: пропускает его вход, встречные выходы
 * превращает в фиксации по плану, а снятое откупает по правилу откупа - и откуп начинает цикл заново.
 * <pre>
 *     new PartialExitSignalSource(slopeChange, 1, 0.9, 0.9, takePlan, reentryRule)
 * </pre>
 * Сторона задаётся снаружи: на каждое направление - свой фиксер, выбирает его
 * {@link RangeSwitchSignalSource} по тренду, а пересоздаёт на смене тренда {@link ResettingSignalSource}.
 *
 * <h2>Цикл</h2>
 * <ol>
 *     <li><b>Ждёт вход.</b> Сигнал источника по стороне фиксера, который открывает ({@link SignalType#opens()})
 *     и по модулю не ниже порога входа, проходит как есть - вход на всю цель. Всё остальное -
 *     {@link Signal#NEUTRAL}.</li>
 *     <li><b>В позиции</b> каждый бар - одно из трёх:
 *     <ul>
 *         <li><b>фиксация</b> - сигнал против стороны, который закрывает ({@link SignalType#closes()}), не
 *         слабее порога выхода, план не выполнен и назвал долю больше нуля:
 *         {@link SignalType#TAKE_PROFIT}, полная уверенность против стороны, доля - от плана;</li>
 *         <li><b>откуп</b> - сигнал по стороне, который открывает, не слабее порога входа, что-то снято, и
 *         {@link ReentryRule правило откупа} разрешает: {@link SignalType#POSITION_ENTRY}, полная
 *         уверенность по стороне, доля - ровно снятое. С этого бара цикл начинается заново, как после
 *         первого входа: снятое и шаги - ноль;</li>
 *         <li>иначе - {@link Signal#NEUTRAL}.</li>
 *     </ul></li>
 * </ol>
 * {@link SignalType#UNSPECIFIED} годится в обе роли: источник, который знает только направление, не знает,
 * вход это или выход, а здесь это знают. Поэтому снизу может стоять и голый {@link SlopeChangeSignalSource}.
 * <p>
 * Доли - от всего счёта: шаги 0.1, 0.2 и 0.2 снимают половину счёта, и откуп после них - тоже половина.
 * Уверенность фиксации и откупа полная, потому что решение принято здесь, по своим порогам, - а пороги
 * стратегии должны их пропустить.
 *
 * <h2>Чего фиксер не знает</h2>
 * Счёта и тренда. Вход, который счёт не исполнил, фиксер всё равно считает позицией. Фиксация больше
 * позиции её не перевернёт, а откуп купит не больше, чем по карману: {@link
 * com.siberalt.singularity.strategy.impl.quantity.TradeQuantity#namedLots}.
 * <p>
 * Источник с порогом ниже порога фиксера кормит его плохо. {@link EntryExitSignalSource} без своего порога
 * входит на первом баре после выхода, где кривизна только что сменила знак: на часовом стенде у таких входов
 * |c| медиана 0.066, и порог 0.9 проходят 5% - фиксер почти не включается.
 *
 * <h2>Замер</h2>
 * Часовой стенд по ликвидной трети, лонг, план 0.1, 0.2, 0.2, пороги 0.9, откуп на всю цель после плана -
 * против скелета без фиксации: над голой кривизной SlopeChangeSignalSource(120) +9.6 ± 4.6, но минус в
 * одном периоде из четырёх; над EntryExit с порогом 0.9 +1.8 ± 3.2. Доля по расстоянию без порога
 * проиграла шагам −9.0 ± 6.0: план почти не выполнялся, и до откупа не доходило.
 *
 * <h2>Состояние</h2>
 * Помнит, в позиции ли он, и {@link TakeProgress}. Один экземпляр - одна бумага, одна сторона, один тренд.
 */
public class PartialExitSignalSource implements SignalSource {
    private final SignalSource base;

    private final double side;

    private final double entryThreshold;

    private final double exitThreshold;

    private final TakePlan plan;

    private final ReentryRule reentry;

    /** {@code null} - позиции нет, ждёт вход. */
    private TakeProgress progress;

    /**
     * @param base           источник входов и выходов
     * @param side           сторона, которую ведёт фиксер: 1 - лонг, −1 - шорт
     * @param entryThreshold с какой уверенности сигнал по стороне - вход или откуп, в (0, 1]
     * @param exitThreshold  с какой уверенности встречный выход - фиксация, в (0, 1]
     * @param plan           сколько снимает каждая фиксация и когда хватит
     * @param reentry        когда снятое откупается
     */
    public PartialExitSignalSource(SignalSource base, double side, double entryThreshold, double exitThreshold,
                                   TakePlan plan, ReentryRule reentry) {
        if (base == null) {
            throw new IllegalArgumentException("Нужен источник входов и выходов");
        }

        if (side != 1 && side != -1) {
            throw new IllegalArgumentException("Сторона фиксера - 1 или −1, получено " + side);
        }

        if (!(entryThreshold > 0) || entryThreshold > 1 || !(exitThreshold > 0) || exitThreshold > 1) {
            throw new IllegalArgumentException(
                "Пороги лежат в (0, 1], получено %s и %s".formatted(entryThreshold, exitThreshold));
        }

        if (plan == null || reentry == null) {
            throw new IllegalArgumentException("Нужны план фиксации и правило откупа");
        }

        this.base = base;
        this.side = side;
        this.entryThreshold = entryThreshold;
        this.exitThreshold = exitThreshold;
        this.plan = plan;
        this.reentry = reentry;
    }

    /** План шагами - {@link StepTakePlan}, откуп после плана - {@link ReentryRule#afterPlan()}. */
    public PartialExitSignalSource(SignalSource base, double side, double entryThreshold, double exitThreshold,
                                   double... steps) {
        this(base, side, entryThreshold, exitThreshold, new StepTakePlan(steps), ReentryRule.afterPlan());
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        Signal signal = base.calculate(lastCandles);
        double conviction = Math.abs(signal.confidence());
        SignalType type = signal.type();
        boolean along = side * signal.confidence() > 0 && conviction >= entryThreshold
            && (type.opens() || type == SignalType.UNSPECIFIED);

        if (progress == null) {
            if (!along) {
                return Signal.NEUTRAL;
            }

            progress = TakeProgress.fresh(side);

            return signal;
        }

        boolean against = side * signal.confidence() < 0 && conviction >= exitThreshold
            && (type.closes() || type == SignalType.UNSPECIFIED);
        boolean done = plan.done(progress);

        if (against && !done) {
            return take(signal, lastCandles);
        }

        if (along && progress.taken() > 0 && reentry.allows(progress, done, lastCandles)) {
            Signal buyBack = new Signal(SignalType.POSITION_ENTRY, side, signal.strength(), progress.taken());

            progress = TakeProgress.fresh(side);

            return buyBack;
        }

        return Signal.NEUTRAL;
    }

    private Signal take(Signal signal, List<Candle> lastCandles) {
        double share = plan.shareOf(progress, lastCandles);

        if (!(share >= 0) || share > 1 || progress.taken() + share > 1 + 1e-9) {
            throw new IllegalStateException(
                "План назвал долю %s при снятом %s: доля вне [0, 1] или вместе больше счёта"
                    .formatted(share, progress.taken()));
        }

        if (share == 0) {
            return Signal.NEUTRAL;
        }

        progress = progress.after(share);

        return new Signal(SignalType.TAKE_PROFIT, -side, signal.strength(), share);
    }
}
