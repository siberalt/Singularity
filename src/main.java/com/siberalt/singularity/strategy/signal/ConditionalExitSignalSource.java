package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCondition;

import java.util.List;

/**
 * Пока условие держится - закрывать позицию. Срок, выраженный сигналом, а не настройкой обёртки.
 * <p>
 * Это то, чем раньше был {@code EntryExitSignalSource.setDeadline}, и переезд сюда возможен ровно потому,
 * что {@link Signal} научился говорить {@link SignalType#POSITION_EXIT}. Пока сигнал умел только знак,
 * «закрыть» было неотличимо от «перевернуться», и источник, не знающий стороны открытой позиции, выразить
 * закрытие не мог - приходилось отдавать условие обёртке, которая сторону знает. Теперь сторону по-прежнему
 * подставляет она, но решение «закрыть» приходит обычным сигналом и складывается с другими выходами через
 * {@link AnyOfSignalSource}.
 * <p>
 * <b>Уверенность здесь единица, а стороны нет.</b> Две разные вещи, и путать их не стоит. Стороны нет
 * потому, что закрытие не про неё: закрыть лонг - продать, закрыть шорт - купить, и знает это тот, кто
 * видит счёт; {@link EntryExitSignalSource} спрашивает {@link SignalType#closes()}, а не знак, и сам
 * подставляет сторону. А уверенность полная потому, что условие либо выполнено, либо нет - окно перед
 * отсечкой это факт календаря, а не оценка.
 * <p>
 * Тип не освобождает от того, чтобы быть убедительным. Пороги существуют, чтобы отсеивать слабые сигналы
 * как шум, и закрытие из этого правила не исключено: источник, которому есть что сказать, говорит это
 * уверенностью, а не рассчитывает, что тип его извинит. Поэтому слабое закрытие ({@code 0.1}) не сработает
 * ни здесь, ни дальше по цепочке - и это осознанный отказ от обхода порогов, а не недоделка.
 * <p>
 * Условие - любое: окно перед дивидендной отсечкой
 * ({@link com.siberalt.singularity.strategy.market.ExDateWindow}), конец сессии, слишком широкий спред.
 * Это и есть причина делать источник над условием, а не отдельный источник под отсечку: «не держать под
 * X» - одна форма правила, и ей ни к чему знать, что такое X.
 */
public class ConditionalExitSignalSource implements SignalSource {
    /**
     * Закрывающий сигнал: полная уверенность, стороны нет. Сторону подставит тот, кто видит позицию, а
     * уверенность названа потому, что без неё сигнал не пройдёт ни один порог - и правильно не пройдёт.
     */
    private static final Signal CLOSING =
        new Signal(SignalType.POSITION_EXIT, 1, 1, Signal.UNDEFINED_BALANCE);

    private final MarketCondition condition;

    public ConditionalExitSignalSource(MarketCondition condition) {
        if (condition == null) {
            throw new IllegalArgumentException("Нет условия, по которому закрывать позицию");
        }

        this.condition = condition;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        return condition.holds(lastCandles) ? CLOSING : Signal.NEUTRAL;
    }
}
