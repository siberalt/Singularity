package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCondition;

import java.util.List;

/**
 * One delegate opens the position and another closes it.
 * <p>
 * The entry delegate is asked until it says something; that something is passed on, and from then on the
 * entry is not consulted at all. The exit delegate is asked instead, and the first thing it says that
 * points the other way is passed on as the closing signal. Then the entry is listened to again. So the
 * holding time is not a number chosen in advance, as with {@link FixedReverserSignalSource},
 * but however long it takes the second opinion to arrive - a fall bought and held until the rebound is
 * called over, rather than for five bars because five measured best.
 * <p>
 * Only the opposite direction closes. A signal from the exit delegate pointing the same way as the
 * position is ignored rather than acted on: it agrees with what is already held, and there is nothing to
 * do about agreement. That matters when choosing what to pass in, because a detector of the exit condition
 * often points the way the exit does not - "the instrument is dear again" is a rise, while leaving a long
 * is a sell. Wrap such a delegate in {@link InvertedSignalSource} and it lines up.
 * <p>
 * Three things here are deliberate, and each was paid for:
 * <ul>
 *   <li><b>Signals are compared by value.</b> Asking whether a delegate returned {@link Signal#NEUTRAL}
 *   by identity passes everything, because a delegate that builds a fresh {@code Signal} on every bar
 *   never returns that constant. Hence {@code minSignal}, which the signal has to reach in absolute
 *   value - by default anything that is not exactly zero.</li>
 *   <li><b>The exit delegate is never asked on the bar the entry fired on</b>: that bar is answered by
 *   the entry alone, so the two never read the same candles. Otherwise an entry could be undone in the
 *   moment it was made, and a position that opens and closes on one bar pays a round trip for nothing.</li>
 *   <li><b>The closing signal is given at full confidence</b>, whatever confidence the exit delegate had.
 *   A strategy acts on a signal only past its own threshold, so a weak exit would be filtered out while
 *   this calculator had already gone back to waiting for an entry - leaving a position with nothing left
 *   to close it. The direction is the closing one; the strength is not the place to be subtle.</li>
 * </ul>
 * By default it cannot give up: if the exit delegate never speaks, the position is held to the end of the
 * run. An exit condition that may simply never occur - a level that is never reached, a reading the
 * instrument does not visit in a quiet year - therefore wants {@link #setMaxWaitBars a limit}, after
 * which this calculator closes the position itself rather than waiting for an opinion that is not coming.
 * <p>
 * A limit that is a date rather than a count of bars goes in {@link #setDeadline} instead. Both close the
 * position without asking the exit delegate, and for the same reason it is this class that does it: the
 * direction the position points is known here and nowhere else, so one condition closes a long and a short
 * alike - which a delegate handing out a signed signal cannot do.
 */
public class EntryExitSignalSource implements SignalSource {
    private final SignalSource entry;

    private final SignalSource exit;

    private final double minSignal;

    private int maxWaitBars = Integer.MAX_VALUE;

    /** Срок, не зависящий от мнения выходного делегата; по умолчанию его нет. */
    private MarketCondition deadline = lastCandles -> false;

    /** Which way the open position points, zero while there is none. */
    private double direction;

    /** Bars the exit delegate has been asked on for the open position. */
    private int waited;

    /**
     * @param entry     what opens the position
     * @param exit      what closes it, by pointing the other way
     * @param minSignal how strong a signal from either has to be, in absolute value, to count
     */
    public EntryExitSignalSource(SignalSource entry, SignalSource exit, double minSignal) {
        if (entry == null || exit == null) {
            throw new IllegalArgumentException("Both an entry and an exit are needed");
        }

        if (minSignal <= 0) {
            throw new IllegalArgumentException("The smallest signal worth acting on must be above zero");
        }

        this.entry = entry;
        this.exit = exit;
        this.minSignal = minSignal;
    }

    /** Acts on any signal that has a direction at all. */
    public EntryExitSignalSource(SignalSource entry, SignalSource exit) {
        this(entry, exit, Double.MIN_VALUE);
    }

    /**
     * How many bars the exit delegate is given to speak before the position is closed anyway.
     * <p>
     * The bars are counted from the one after the entry, which is the first the exit is asked on, and the
     * closing signal on the last of them is the same one the exit would have produced. Unlimited by
     * default, which is right when the exit is something that must happen - a bounce ending, a reading
     * coming back to the middle - and wrong when it is something that merely might. A trade held for
     * months because nobody said to leave is not a trade anybody chose, and this is the guard against it.
     */
    public EntryExitSignalSource setMaxWaitBars(int maxWaitBars) {
        if (maxWaitBars < 1) {
            throw new IllegalArgumentException("A position has to be given at least one bar, got "
                + maxWaitBars);
        }

        this.maxWaitBars = maxWaitBars;

        return this;
    }

    /**
     * Условие, по которому позицию закрывают независимо от мнения выходного делегата.
     * <p>
     * Тот же срок, что {@link #setMaxWaitBars}, только измеряемый не барами, а состоянием рынка - и этим
     * он решает задачу, которую калькулятором выхода не решить. Закрывающий сигнал синтезирует эта обёртка,
     * а она одна здесь знает, куда смотрит открытая позиция, поэтому одно и то же условие закрывает и лонг,
     * и шорт. Делегат выхода на такое не способен: он выдаёт знак, не зная стороны, и закрывал бы только
     * одну из них.
     * <p>
     * Пример, ради которого это и сделано, - {@link com.siberalt.singularity.strategy.market.ExDateWindow}:
     * «не держать под дивидендную отсечку» не мнение о рынке, а срок, и держать его надо для любой стороны.
     * <p>
     * Проверяется он со следующего после входа бара - как и мнение делегата, и по той же причине: бар входа
     * отвечает вход, иначе позиция закрылась бы в момент открытия, заплатив круг ни за что. Поэтому вход,
     * сделанный внутри окна, сроком не перекрывается, и запрет на вход - отдельное правило.
     */
    public EntryExitSignalSource setDeadline(MarketCondition deadline) {
        if (deadline == null) {
            throw new IllegalArgumentException("Нет условия, по которому закрывать позицию");
        }

        this.deadline = deadline;

        return this;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (direction != 0) {
            Signal signal = exit.calculate(lastCandles);
            boolean closes = Math.abs(signal.confidence()) >= minSignal && direction * signal.confidence() < 0;

            waited++;

            if (!closes && waited < maxWaitBars && !deadline.holds(lastCandles)) {
                return Signal.NEUTRAL;
            }

            double closing = -direction;

            direction = 0;

            // Закрытие названо явно: обёртка - единственный, кто знает сторону открытой позиции, и
            // именно здесь "закрыть" перестаёт быть неотличимым от "перевернуться".
            return new Signal(SignalType.POSITION_EXIT, closing, 1, Signal.UNDEFINED_BALANCE);
        }

        Signal signal = entry.calculate(lastCandles);

        if (Math.abs(signal.confidence()) < minSignal) {
            return Signal.NEUTRAL;
        }

        direction = Math.signum(signal.confidence());
        waited = 0;

        return signal;
    }
}
