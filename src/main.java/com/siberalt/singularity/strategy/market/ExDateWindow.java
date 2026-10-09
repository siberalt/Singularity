package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.time.Instant;
import java.util.List;

/**
 * Окно перед дивидендной отсечкой: до дня покупки осталось меньше {@code daysAhead} дней или он наступил.
 * <p>
 * Это {@link MarketCondition}, а не {@link com.siberalt.singularity.strategy.signal.SignalSource} и не
 * {@link com.siberalt.singularity.strategy.impl.quantity.TradeQuantity}, и за этим стоит разбор, который
 * стоит записать, потому что первые два варианта напрашиваются первыми.
 * <ul>
 *   <li><b>Размер не годится по коду.</b> {@code TradeQuantity} спрашивают только после того, как сигнал
 *   перешёл порог. Под отсечку сигнал бычий - иначе позицию бы не держали, - ветка продажи не вызывается
 *   вовсе, и нулевой размер запретит только доливку, оставив позицию на месте.</li>
 *   <li><b>Калькулятор не годится по словарю.</b> В {@code Signal} есть знак, но нет различия между
 *   «закрыть» и «перевернуться»: минус единица для книги, умеющей шортить, означает «зашортись». Калькулятор
 *   к тому же не знает, куда смотрит открытая позиция, поэтому закрывать умел бы только одну сторону.</li>
 *   <li><b>Условие годится, потому что направление знает тот, кто его спрашивает.</b>
 *   {@link com.siberalt.singularity.strategy.signal.EntryExitSignalSource} помнит сторону открытой
 *   позиции и сам синтезирует закрывающий сигнал - и для настоящего выхода, и для срока. Отданное ему
 *   сроком, это окно закрывает и лонг, и шорт одним объектом.</li>
 * </ul>
 * Шорт под отсечку закрывать надо не меньше, чем лонг, и по худшей причине: по нему дивиденд платят, а не
 * получают, и в этой базе измерено, что гэп (2.10%) его не отрабатывает (6.08%).
 *
 * <h2>Почему окно шире одного дня</h2>
 * Гэп случается на следующий торговый день после дня покупки. Заявка в этом проекте доходит до рынка через
 * бар: решение, принятое в сам день покупки, исполнится по открытию дня отсечки, то есть <b>уже после
 * гэпа</b>, и выход потеряет смысл. Поэтому по умолчанию {@value #DEFAULT_DAYS_AHEAD} дня - день покупки и
 * день перед ним: сработав днём раньше, заявка успевает исполниться по открытию дня покупки, до гэпа.
 * <p>
 * Шире без нужды делать не стоит: каждый лишний день - день вне позиции, а в этой базе уже измерено,
 * сколько стоит преждевременный выход из тренда. Ширину окна надо мерить, а не выбирать.
 *
 * <h2>Два применения, и оба нужны</h2>
 * <b>Срок</b> закрывает то, что держится: {@code entryExit.setDeadline(window)}. <b>Отрицание</b> не даёт
 * войти: {@code SignalCondition.of(window.negated()).onlyForBuys()}. Без второго правило будет честно
 * закрывать позицию и честно открывать её обратно до самого гэпа - срок проверяется со следующего после
 * входа бара, так что вход за день до отсечки им не перекрывается. И {@code onlyForBuys} обязателен:
 * симметричное условие запретило бы и продажу, то есть сам выход, ради которого всё делается.
 */
public class ExDateWindow implements MarketCondition {
    /** День покупки и день перед ним - минимум, при котором выход происходит до гэпа. */
    public static final int DEFAULT_DAYS_AHEAD = 2;

    private final DividendCalendar calendar;

    private final int daysAhead;

    /**
     * @param calendar  чем узнавать отсечки; обязан учитывать только объявленное к спрашиваемому моменту
     * @param daysAhead за сколько дней до дня покупки включительно окно открывается
     */
    public ExDateWindow(DividendCalendar calendar, int daysAhead) {
        if (calendar == null) {
            throw new IllegalArgumentException("Без календаря отсечек окно не построить");
        }

        if (daysAhead < 1) {
            throw new IllegalArgumentException(
                "Окно меряется днями и не может быть меньше одного, получено " + daysAhead);
        }

        this.calendar = calendar;
        this.daysAhead = daysAhead;
    }

    public ExDateWindow(DividendCalendar calendar) {
        this(calendar, DEFAULT_DAYS_AHEAD);
    }

    /**
     * Бумага берётся из свечей, а не из конструктора: календарь спрашивают ключом, которым бумага названа в
     * свечах, и тогда один экземпляр окна обслуживает весь набор бумаг прогона.
     */
    @Override
    public boolean holds(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return false;
        }

        Candle last = lastCandles.getLast();
        Instant lastBuyDate = calendar.nextLastBuyDate(last.instrumentId(), last.getTime());

        if (lastBuyDate == null) {
            return false;
        }

        // Днями, а не мгновениями, и строго меньше: при daysAhead = 2 окно - это день покупки и день
        // перед ним. Разница мгновений дала бы здесь три дня, потому что бар стоит в середине своего дня.
        return DividendCalendar.dayOf(lastBuyDate) - DividendCalendar.dayOf(last.getTime()) < daysAhead;
    }
}
