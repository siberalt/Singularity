package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сигнал: о чём он, в какую сторону и какую долю счёта просит. */
class SignalTest {
    /** Форма, которой отвечают почти все источники: направление есть, мнения о позиции нет. */
    @Test
    void saysNothingAboutThePositionWhenBuiltFromDirectionAlone() {
        Signal signal = new Signal(0.8, 0.3);

        assertEquals(SignalType.UNSPECIFIED, signal.type());
        assertFalse(signal.hasPositionBalance());
        assertEquals(0.8, signal.confidence());
        assertEquals(0.3, signal.strength());
    }

    /**
     * Молчание и посчитанный ноль - разные вещи, и тип их различает. «RSI ровно 50» означает измеренное
     * «ни туда ни сюда», а {@link Signal#NEUTRAL} - «сказать нечего»; до появления типа это было одно
     * значение, и источнику с нулём приходилось выдавать себя за молчащего.
     */
    @Test
    void tellsSilenceApartFromAMeasuredZero() {
        assertEquals(SignalType.NONE, Signal.NEUTRAL.type());
        assertNotEquals(Signal.NEUTRAL, new Signal(0, 0));
        assertEquals(SignalType.UNSPECIFIED, new Signal(0, 0).type());
    }

    @Test
    void doesNotPretendToKnowABalanceItWasNotGiven() {
        assertFalse(Signal.NEUTRAL.hasPositionBalance());
        assertFalse(new Signal(1, 1).hasPositionBalance());
        assertTrue(new Signal(1, 1).withPositionBalance(0).hasPositionBalance());
        assertTrue(new Signal(1, 1).withPositionBalance(0.4).hasPositionBalance());
    }

    /** Ноль - это «встать в деньги», а не «доли нет»: различие несёт {@link Signal#hasPositionBalance()}. */
    @Test
    void takesZeroAsAnAnswerRatherThanAsSilence() {
        Signal flat = new Signal(1, 1).withPositionBalance(0);

        assertTrue(flat.hasPositionBalance());
        assertEquals(0, flat.positionBalance());
    }

    @Test
    void refusesABalanceOutsideItsRange() {
        assertThrows(IllegalArgumentException.class, () -> new Signal(1, 1).withPositionBalance(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new Signal(1, 1).withPositionBalance(1.1));
        assertThrows(IllegalArgumentException.class,
            () -> new Signal(SignalType.POSITION_ENTRY, 1, 1, 2));
    }

    @Test
    void refusesASignalThatDoesNotSayWhatItIsAbout() {
        assertThrows(IllegalArgumentException.class, () -> new Signal(null, 1, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new Signal(1, 1).withType(null));
    }

    @Test
    void keepsEverythingElseWhenOnePartIsChanged() {
        Signal typed = new Signal(-0.6, 0.2).withType(SignalType.STOP_LOSS);

        assertEquals(SignalType.STOP_LOSS, typed.type());
        assertEquals(-0.6, typed.confidence());
        assertEquals(0.2, typed.strength());
        assertFalse(typed.hasPositionBalance());

        Signal sized = typed.withPositionBalance(0.25);

        assertEquals(SignalType.STOP_LOSS, sized.type());
        assertEquals(-0.6, sized.confidence());
        assertEquals(0.2, sized.strength());
        assertEquals(0.25, sized.positionBalance());
    }

    /**
     * «Это выход?» - вопрос к {@link SignalType#closes()}. Сравнение с одним {@code POSITION_EXIT} молча
     * пропустило бы стоп и цель, а ловить такое трудно: стопы срабатывают редко.
     */
    @Test
    void answersWhetherItOpensOrCloses() {
        assertTrue(SignalType.POSITION_ENTRY.opens());
        assertFalse(SignalType.POSITION_ENTRY.closes());

        for (SignalType closing : new SignalType[]{SignalType.POSITION_EXIT, SignalType.TAKE_PROFIT,
            SignalType.STOP_LOSS}) {
            assertTrue(closing.closes(), closing + " закрывает позицию");
            assertFalse(closing.opens(), closing + " не открывает");
        }
    }

    /** Ни молчание, ни мнение об одном направлении про позицию не говорят ничего. */
    @Test
    void saysNeitherForTheTypesThatAreNotAboutAPosition() {
        for (SignalType neither : new SignalType[]{SignalType.NONE, SignalType.UNSPECIFIED}) {
            assertFalse(neither.opens(), neither + " не открывает");
            assertFalse(neither.closes(), neither + " не закрывает");
        }
    }
}
