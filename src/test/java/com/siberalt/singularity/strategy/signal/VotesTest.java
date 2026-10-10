package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Расклад голосов и политики, которые из него выводят тип и долю счёта. */
class VotesTest {
    /**
     * Главное различие, ради которого {@link Votes} и существует. Трое из пяти за, двое против: сложенная
     * уверенность - разность сторон, то есть 0.2, а согласный вес - сумма одной из них, то есть 0.6.
     * Размер позиции по этим двум числам выходит втрое разный.
     */
    @Test
    void tellsAgreementApartFromNetConfidence() {
        Votes split = new Votes(0.2, 1, 1, 0.6, 0.4);

        assertEquals(0.6, split.agreeingWeight(), 1e-9);
        assertEquals(0.6, split.agreeingShare(), 1e-9);
    }

    /** На продаже согласным оказывается другая сторона. */
    @Test
    void countsTheOtherSideWhenTheAnswerIsASell() {
        Votes selling = new Votes(-0.2, 1, 1, 0.4, 0.6);

        assertEquals(0.6, selling.agreeingWeight(), 1e-9);
    }

    /** Ровный раскол: ответа нет, согласных с ним тоже нет. */
    @Test
    void findsNobodyAgreeingWithAnAnswerThatIsNotThere() {
        Votes even = new Votes(0, 1, 1, 0.5, 0.5);

        assertEquals(0, even.agreeingWeight(), 1e-9);
        assertEquals(0, even.agreeingShare(), 1e-9);
    }

    @Test
    void survivesAVoteNobodyCastAt() {
        assertEquals(0, new Votes(0, 0, 0, 0, 0).agreeingShare(), 1e-9);
    }

    @Test
    void readsTheTypeOffThePolicyItWasGiven() {
        Votes buying = new Votes(0.2, 1, 1, 0.6, 0.4);
        Votes selling = new Votes(-0.2, 1, 1, 0.4, 0.6);
        Votes even = new Votes(0, 1, 1, 0.5, 0.5);

        assertEquals(SignalType.UNSPECIFIED, TypePolicy.UNDECIDED.of(buying));
        assertEquals(SignalType.POSITION_ENTRY, TypePolicy.BY_SIDE.of(buying));
        assertEquals(SignalType.POSITION_EXIT, TypePolicy.BY_SIDE.of(selling));
        assertEquals(SignalType.UNSPECIFIED, TypePolicy.BY_SIDE.of(even));
        assertEquals(SignalType.STOP_LOSS, TypePolicy.fixed(SignalType.STOP_LOSS).of(selling));
    }

    @Test
    void readsTheBalanceOffThePolicyItWasGiven() {
        Votes buying = new Votes(0.2, 1, 1, 0.6, 0.4);

        assertEquals(Signal.UNDEFINED_BALANCE, BalancePolicy.UNDEFINED.of(buying));
        assertEquals(0.6, BalancePolicy.AGREEING_WEIGHT.of(buying), 1e-9);
        assertEquals(0.3, BalancePolicy.agreeingWeightUpTo(0.5).of(buying), 1e-9);
    }

    @Test
    void refusesPoliciesThatCannotBeBuilt() {
        assertThrows(IllegalArgumentException.class, () -> TypePolicy.fixed(null));
        assertThrows(IllegalArgumentException.class, () -> BalancePolicy.agreeingWeightUpTo(0));
        assertThrows(IllegalArgumentException.class, () -> BalancePolicy.agreeingWeightUpTo(1.5));
    }
}
