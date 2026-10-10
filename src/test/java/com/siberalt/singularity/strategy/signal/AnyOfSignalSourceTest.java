package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Первый, кто скажет хоть что-то: сложение распоряжений порядком, а не усреднением. */
class AnyOfSignalSourceTest {
    private static final List<Candle> ANY_CANDLES = List.of(Candle.of(TimePoint.NULL, 100.0));

    @Test
    void answersWithTheFirstSourceThatSpeaks() {
        SignalSource chain = new AnyOfSignalSource(
            saying(Signal.NEUTRAL),
            saying(new Signal(0.7, 1)),
            saying(new Signal(-1, 1))
        );

        assertEquals(0.7, chain.calculate(ANY_CANDLES).confidence());
    }

    /** Порядок и есть приоритет: обязательный выход, поставленный первым, перебивает условный. */
    @Test
    void letsTheEarlierSourceWinWhenBothSpeak() {
        Signal mandatory = new Signal(0, 1).withType(SignalType.POSITION_EXIT);

        assertEquals(SignalType.POSITION_EXIT, new AnyOfSignalSource(
            saying(mandatory), saying(new Signal(-1, 1))).calculate(ANY_CANDLES).type());
        assertEquals(SignalType.UNSPECIFIED, new AnyOfSignalSource(
            saying(new Signal(-1, 1)), saying(mandatory)).calculate(ANY_CANDLES).type());
    }

    /** За сработавшим не спрашивают - важно, когда дальше стоит что-то, считающее бары. */
    @Test
    void doesNotAskPastTheOneThatAnswered() {
        List<Integer> asked = new ArrayList<>();
        SignalSource chain = new AnyOfSignalSource(
            saying(new Signal(1, 1)),
            candles -> {
                asked.add(1);

                return Signal.NEUTRAL;
            }
        );

        chain.calculate(ANY_CANDLES);

        assertEquals(List.of(), asked);
    }

    /**
     * Закрытие без стороны - это ответ: тип закрывающий, значит это распоряжение. Ровно так выглядит
     * {@link ConditionalExitSignalSource} - с полной уверенностью и без знака, потому что знак подставит
     * тот, кто видит позицию, а силу источник обязан назвать сам.
     */
    @Test
    void countsATypedCloseAsAnAnswer() {
        SignalSource chain = new AnyOfSignalSource(
            saying(new Signal(0, 1).withType(SignalType.POSITION_EXIT)),
            saying(new Signal(1, 1))
        );

        assertEquals(SignalType.POSITION_EXIT, chain.calculate(ANY_CANDLES).type());
    }

    /**
     * А посчитанный ноль список не обрывает: он никуда не указывает и ничего не велит, так что
     * следующему есть что сказать. Это то же различие, что между молчанием и нулём.
     */
    @Test
    void walksPastAMeasuredZero() {
        SignalSource chain = new AnyOfSignalSource(
            saying(new Signal(0, 0.8)),
            saying(new Signal(-0.5, 1))
        );

        assertEquals(-0.5, chain.calculate(ANY_CANDLES).confidence());
    }

    @Test
    void staysSilentWhenNobodySpeaks() {
        assertEquals(Signal.NEUTRAL, new AnyOfSignalSource(
            saying(Signal.NEUTRAL), saying(new Signal(0, 0))).calculate(ANY_CANDLES));
    }

    @Test
    void refusesAChainItCannotWalk() {
        assertThrows(IllegalArgumentException.class, () -> new AnyOfSignalSource(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AnyOfSignalSource((List<SignalSource>) null));
        // Список из List.of сам отвергает null, поэтому проверка обходом - по изменяемому списку.
        List<SignalSource> withNull = new ArrayList<>();

        withNull.add(null);

        assertThrows(IllegalArgumentException.class, () -> new AnyOfSignalSource(withNull));
    }

    private static SignalSource saying(Signal signal) {
        return candles -> signal;
    }
}
