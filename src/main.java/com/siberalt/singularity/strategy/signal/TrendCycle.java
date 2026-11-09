package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.strategy.market.TrendZone;

/**
 * Позиция внутри тренда как автомат: на входе тренд и наклон текущей ноги, на выходе целевая доля счёта
 * со знаком. Решает только это - ни свечей, ни счёта он не видит, поэтому один и тот же автомат ведут и
 * стенд, и {@link TrendCycleTarget} в стратегии, и расходиться им нечему.
 *
 * <h2>Правила</h2>
 * <ol>
 *     <li><b>Смена тренда закрывает позицию.</b> Любая: вверх на вниз, на «не определён» и обратно.
 *     Позиция открывалась под тренд, тренда больше нет - держать её не за что.</li>
 *     <li><b>Вход - на весь счёт, когда нога идёт по тренду круче порога:</b> {@code сторона · наклон >
 *     порог}. Тренд размечен по закрытым дневным барам и запаздывает на ширину полосы; порог ждёт, пока
 *     и часовая нога подтвердит направление, а не входит на первом же часе после закрытия дня.</li>
 *     <li>Вошли - держим до смены тренда, что бы ни делал наклон.</li>
 * </ol>
 * Шорт - только если разрешён; без него нисходящий тренд - это деньги.
 *
 * <h2>Контроль - вход без порога</h2>
 * {@link #immediate} входит на первом же баре тренда, наклона не спрашивая. Это та же сборка, что
 * «в бумаге, пока полоса говорит вверх», и сравнивать порог надо именно с ней: разница между ними и есть
 * то, что даёт ожидание подтверждения.
 * <p>
 * Замерено на часовом стенде по ликвидной трети (тренд - полоса 2.5 с сужением и отменой, наклон -
 * {@code PivotSlopeSignalSource(4)} по 300 часам, порог 0.118 - медиана |s| на первом часе нового тренда):
 * в лонге порог <b>проиграл</b> входу сразу, −9.6 ± 4.0 пп с издержками, и в каждом из четырёх
 * периодов. Половина входов не задерживается вовсе - на смене нога уже идёт по тренду в 95% случаев, -
 * а ожидание остальных стоит в среднем 1% хода и 7% трендов без входа. Полоса уже запаздывает на свою
 * ширину, и ждать сверх неё - отдавать начало хода. Шорт там же проиграл версии без шорта, −47.5 ± 20.7.
 * <p>
 * Наклон от смены тренда, а не от разворота (та же формула и тот же порог, другой якорь), ложные тренды
 * отсеивает: 28% восходящих трендов остаются без входа, и вход сразу терял на них в среднем 4% хода, а
 * длятся они вдвое короче вошедших. Но на вошедших ожидание отдаёт в среднем 3% хода, и в сумме это
 * дороже: −16.3 ± 5.9 против входа сразу, минус в трёх периодах из четырёх.
 *
 * <h2>Состояние</h2>
 * Автомат помнит тренд и цель, поэтому один экземпляр - одна бумага и один прогон. Первый же тренд,
 * который он увидел, для него новый: начатый на середине прогон входит по тем же правилам.
 */
public class TrendCycle {
    private final double entryThreshold;

    private final boolean shorts;

    private double trend = Double.NaN;

    private double target = 0;

    /**
     * @param entryThreshold насколько круто нога должна идти по тренду, чтобы войти, - в единицах
     *                       наклона, который будет подан; у {@link PivotSlopeSignalSource} это
     *                       {@code tanh(наклон / волатильность)}, то есть меньше единицы
     * @param shorts         открывать ли шорт в нисходящем тренде
     */
    public TrendCycle(double entryThreshold, boolean shorts) {
        if (Double.isNaN(entryThreshold) || entryThreshold >= 1) {
            throw new IllegalArgumentException("Порог входа меньше единицы, получено " + entryThreshold);
        }

        this.entryThreshold = entryThreshold;
        this.shorts = shorts;
    }

    /** Вход на первом же баре тренда, без порога, - контроль для порога. */
    public static TrendCycle immediate(boolean shorts) {
        return new TrendCycle(Double.NEGATIVE_INFINITY, shorts);
    }

    /**
     * Следующий бар.
     *
     * @param trend {@link TrendZone#UP}, {@link TrendZone#DOWN} или {@link Double#NaN}, если тренд не
     *              определён
     * @param slope наклон текущей ноги, со знаком; {@link Double#NaN}, если его не измерить, - тогда
     *              входа по порогу нет
     * @return целевая доля счёта: 1 - весь счёт в лонге, -1 - в шорте, 0 - в деньгах
     */
    public double next(double trend, double slope) {
        if (!Double.isNaN(trend) && trend != TrendZone.UP && trend != TrendZone.DOWN) {
            throw new IllegalArgumentException("Тренд - вверх, вниз или не определён, получено " + trend);
        }

        if (Double.compare(trend, this.trend) != 0) {
            this.trend = trend;
            target = 0;
        }

        if (target == 0 && allowed(trend) && entering(trend, slope)) {
            target = trend;
        }

        return target;
    }

    /** Цель после последнего бара; до первого - ноль. */
    public double target() {
        return target;
    }

    private boolean allowed(double trend) {
        return trend == TrendZone.UP || trend == TrendZone.DOWN && shorts;
    }

    private boolean entering(double side, double slope) {
        return entryThreshold == Double.NEGATIVE_INFINITY || side * slope > entryThreshold;
    }
}
