package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.service.instrument.common.Dividend;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.signal.AnyOfSignalSource;
import com.siberalt.singularity.strategy.signal.ConditionalExitSignalSource;
import com.siberalt.singularity.strategy.signal.EntryExitSignalSource;
import com.siberalt.singularity.strategy.signal.SignalCondition;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;
import com.siberalt.singularity.strategy.signal.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Окно перед отсечкой: срок для открытой позиции и запрет на вход. */
class ExDateWindowTest {
    private static final long MINE = 7;
    private static final long OTHER = 36;
    private static final Instant LAST_BUY = Instant.parse("2024-06-10T00:00:00Z");
    private static final Instant DECLARED = Instant.parse("2024-05-01T12:00:00Z");

    /**
     * Окно по умолчанию - день покупки и день перед ним, ровно два дня, а не три: бар стоит в середине
     * своего дня, и разница мгновений дала бы здесь лишний день.
     */
    @Test
    void coversTheDayBeforeAndTheDayItself() {
        ExDateWindow window = new ExDateWindow(calendar());

        assertTrue(window.holds(barAt(MINE, "2024-06-10T10:00:00Z")), "сам день покупки");
        assertTrue(window.holds(barAt(MINE, "2024-06-09T10:00:00Z")), "день перед ним");
        assertFalse(window.holds(barAt(MINE, "2024-06-08T10:00:00Z")), "за два дня - рано");
        assertFalse(window.holds(barAt(MINE, "2024-06-11T10:00:00Z")), "отсечка прошла");
    }

    /** Бумага берётся из свечей: у чужой отсечки в тот же день окно не открывается. */
    @Test
    void asksAboutTheInstrumentTheCandlesName() {
        ExDateWindow window = new ExDateWindow(calendar());

        assertTrue(window.holds(barAt(MINE, "2024-06-09T10:00:00Z")));
        assertFalse(window.holds(barAt(OTHER, "2024-06-09T10:00:00Z")));
    }

    /** Пока о дивиденде не объявлено, его нет. */
    @Test
    void knowsNothingBeforeTheAnnouncement() {
        ExDateWindow window = new ExDateWindow(calendar());

        assertFalse(window.holds(barAt(MINE, "2024-04-30T10:00:00Z")));
        assertTrue(window.holds(barAt(MINE, "2024-06-09T10:00:00Z")));
    }

    /**
     * Выходным делегатом оно закрывает и лонг, и шорт - одним объектом, без зеркала и без знания стороны.
     * <p>
     * Раньше это приходилось отдавать обёртке настройкой {@code setDeadline}, потому что источник умел
     * только знак и закрыл бы одну сторону из двух. Теперь источник говорит «закрыть» типом, а сторону
     * подставляет обёртка, которая её и знает.
     */
    @Test
    void closesEitherSideAsAnExitDelegate() {
        for (double side : new double[]{1, -1}) {
            SignalSource entry = candles -> new Signal(side, 1);
            EntryExitSignalSource rule = new EntryExitSignalSource(entry,
                new ConditionalExitSignalSource(new ExDateWindow(calendar())));

            // Вход далеко от отсечки, затем бар внутри окна.
            assertEquals(side, rule.calculate(barAt(MINE, "2024-06-01T10:00:00Z")).confidence());

            Signal closing = rule.calculate(barAt(MINE, "2024-06-09T10:00:00Z"));

            assertEquals(1, closing.strength(), "закрытие выдаётся с полной уверенностью");
            assertEquals(SignalType.POSITION_EXIT, closing.type());
            assertEquals(-side, closing.confidence(),
                "закрывающий сигнал противоположен стороне позиции");
        }
    }

    /** И складывается с другими выходами порядком, а не усреднением: обязательный ставится раньше. */
    @Test
    void winsOverTheRulesOwnExitWhenBothCouldSpeak() {
        SignalSource rulesExit = candles -> new Signal(-1, 1);
        SignalSource exits = new AnyOfSignalSource(
            new ConditionalExitSignalSource(new ExDateWindow(calendar())),
            rulesExit
        );

        assertEquals(SignalType.POSITION_EXIT,
            exits.calculate(barAt(MINE, "2024-06-09T10:00:00Z")).type());
        // Вне окна отвечает своё правило, и его ответ проходит как был.
        assertEquals(SignalType.UNSPECIFIED,
            exits.calculate(barAt(MINE, "2024-06-01T10:00:00Z")).type());
    }

    /** Без срока та же обёртка позицию держит: срок ничего не меняет, пока его не поставили. */
    @Test
    void holdsOnWithoutADeadline() {
        EntryExitSignalSource rule = new EntryExitSignalSource(
            candles -> new Signal(1, 1), candles -> Signal.NEUTRAL);

        assertEquals(1, rule.calculate(barAt(MINE, "2024-06-01T10:00:00Z")).confidence());
        assertEquals(Signal.NEUTRAL, rule.calculate(barAt(MINE, "2024-06-09T10:00:00Z")));
    }

    /**
     * Отрицание того же окна не даёт войти, и ставится оно только на покупку: симметричное запретило бы
     * продажу, то есть сам выход под отсечку.
     */
    @Test
    void refusesEntryOverTheSameWindow() {
        SignalCondition away = SignalCondition.of(new ExDateWindow(calendar()).negated()).onlyForBuys();
        List<Candle> inside = barAt(MINE, "2024-06-09T10:00:00Z");

        assertFalse(away.holds(inside, () -> new Signal(1, 1)), "покупка запрещена");
        assertTrue(away.holds(inside, () -> new Signal(-1, 1)), "продажа проходит");
        assertTrue(away.holds(barAt(MINE, "2024-06-08T10:00:00Z"), () -> new Signal(1, 1)));
    }

    /** Дивиденд без даты объявления или без дня покупки выбрасывается, и потеря видна. */
    @Test
    void dropsWhatItCannotDate() {
        AnnouncedDividendCalendar calendar = new AnnouncedDividendCalendar(Map.of(MINE, List.of(
            dividend(DECLARED, LAST_BUY),
            dividend(null, LAST_BUY),
            dividend(DECLARED, null)
        )));

        assertEquals(2, calendar.skipped());
        assertEquals(LAST_BUY, calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T00:00:00Z")));
    }

    /**
     * Дивиденд, объявленный не раньше своего же дня покупки, считается отдельно: отреагировать на него
     * нельзя, и без счётчика «правило ни разу не сработало» выглядело бы как «отсечек не было». У Сбера
     * такой в данных есть - объявлен 2026-07-18 при дне покупки 2026-07-17.
     */
    @Test
    void countsWhatWasAnnouncedTooLateToActOn() {
        AnnouncedDividendCalendar calendar = new AnnouncedDividendCalendar(Map.of(MINE, List.of(
            dividend(Instant.parse("2026-07-18T00:00:00Z"), Instant.parse("2026-07-17T00:00:00Z")),
            dividend(DECLARED, LAST_BUY)
        )));

        assertEquals(1, calendar.tooLate());
        assertEquals(0, calendar.skipped());
        // Он остаётся в календаре - виден с даты объявления, просто окно к тому времени уже прошло.
        assertNull(calendar.nextLastBuyDate(MINE, Instant.parse("2026-07-16T00:00:00Z")));
    }

    /**
     * Бумага, которой календарь не знает, отвечает «отсечек нет» - и отличить это от «не платит» изнутри
     * нельзя, поэтому набор проверяется снаружи.
     */
    @Test
    void tellsWhichInstrumentsItHolds() {
        AnnouncedDividendCalendar calendar = new AnnouncedDividendCalendar(
            Map.of(MINE, List.of(dividend(DECLARED, LAST_BUY))));

        assertTrue(calendar.knows(MINE));
        assertFalse(calendar.knows(OTHER));
        assertEquals(null, calendar.nextLastBuyDate(OTHER, Instant.parse("2024-06-09T00:00:00Z")));
    }

    /** Ближайшая известная отсечка - именно ближайшая, а не первая по списку. */
    @Test
    void takesTheNearestAnnouncedOne() {
        DividendCalendar calendar = new AnnouncedDividendCalendar(Map.of(MINE, List.of(
            dividend(DECLARED, Instant.parse("2024-12-10T00:00:00Z")),
            dividend(DECLARED, LAST_BUY)
        )));

        assertEquals(LAST_BUY, calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T00:00:00Z")));
        assertEquals(Instant.parse("2024-12-10T00:00:00Z"),
            calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-11T00:00:00Z")));
    }

    /** Окно шире - выход раньше, и это измеряемый параметр, а не константа. */
    @Test
    void takesAWiderWindowWhenAsked() {
        ExDateWindow window = new ExDateWindow(calendar(), 4);

        assertTrue(window.holds(barAt(MINE, "2024-06-07T10:00:00Z")));
        assertFalse(window.holds(barAt(MINE, "2024-06-06T10:00:00Z")));
    }

    @Test
    void saysNothingWithoutACalendar() {
        ExDateWindow window = new ExDateWindow(DividendCalendar.EMPTY);

        assertFalse(window.holds(barAt(MINE, "2024-06-10T10:00:00Z")));
        assertFalse(window.holds(null));
        assertFalse(window.holds(List.of()));
    }

    @Test
    void refusesWhatCannotBeComputed() {
        assertThrows(IllegalArgumentException.class, () -> new ExDateWindow(null));
        assertThrows(IllegalArgumentException.class,
            () -> new ExDateWindow(DividendCalendar.EMPTY, 0));
        assertThrows(IllegalArgumentException.class, () -> new AnnouncedDividendCalendar(null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionalExitSignalSource(null));
        assertThrows(IllegalArgumentException.class, () -> new AnyOfSignalSource(List.of()));
    }

    private static DividendCalendar calendar() {
        return new AnnouncedDividendCalendar(Map.of(MINE, List.of(dividend(DECLARED, LAST_BUY))));
    }

    private static Dividend dividend(Instant declared, Instant lastBuy) {
        return new Dividend()
            .setDeclaredDate(declared)
            .setLastBuyDate(lastBuy)
            .setDividendNet(Money.of("RUB", 10.0));
    }

    private static List<Candle> barAt(long instrumentId, String time) {
        Quotation price = Quotation.of(100);

        return List.of(new Candle(instrumentId, new TimePoint(1, Instant.parse(time)),
            price, price, price, price, 1));
    }
}
