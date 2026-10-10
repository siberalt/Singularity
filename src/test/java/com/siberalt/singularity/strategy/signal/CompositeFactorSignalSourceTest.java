package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Состав из взвешенных источников: что он складывает и что сам решает про тип и размер. */
class CompositeFactorSignalSourceTest {
    private static final List<Candle> ANY_CANDLES = List.of(Candle.of(TimePoint.NULL, 100.0));

    /**
     * Молчит о том, чего не знает: тип делегаты не подскажут, долю счёта - тоже. Это поведение по
     * умолчанию, и на нём написаны все существующие прогоны.
     */
    @Test
    void saysNothingAboutTypeOrSizeUnlessAsked() {
        Signal merged = committee().calculate(ANY_CANDLES);

        assertEquals(SignalType.UNSPECIFIED, merged.type());
        assertFalse(merged.hasPositionBalance());
    }

    /**
     * Трое из пяти за, двое против. Уверенность - разность сторон: {@code (3 - 2) / 5 = 0.2}. Доля
     * согласного веса - сумма одной стороны: {@code 3 / 5 = 0.6}. Втрое разный размер позиции из одного
     * расклада, и именно поэтому доля существует отдельно от уверенности.
     */
    @Test
    void sizesByHowMuchOfTheCommitteeAgreed() {
        Signal merged = committee()
            .setBalance(BalancePolicy.AGREEING_WEIGHT)
            .calculate(ANY_CANDLES);

        assertEquals(0.2, merged.confidence(), 1e-9);
        assertTrue(merged.hasPositionBalance());
        assertEquals(0.6, merged.positionBalance(), 1e-9);
    }

    @Test
    void takesTheTypeItWasTold() {
        assertEquals(SignalType.POSITION_ENTRY,
            committee().setType(SignalType.POSITION_ENTRY).calculate(ANY_CANDLES).type());
    }

    /** Знак как тип - для книги без шортов: состав в плюсе, значит это вход. */
    @Test
    void readsTheSideAsATypeWhenToldTo() {
        assertEquals(SignalType.POSITION_ENTRY,
            committee().setType(TypePolicy.BY_SIDE).calculate(ANY_CANDLES).type());
        assertEquals(SignalType.POSITION_EXIT, bearishCommittee()
            .setType(TypePolicy.BY_SIDE).calculate(ANY_CANDLES).type());
    }

    /** Веса не обязаны быть равными, и доля считается по весу, а не по числу голосов. */
    @Test
    void weighsTheVotesRatherThanCountingThem() {
        Signal merged = new CompositeFactorSignalSource.Builder()
            .addSource(saying(1), 3)
            .addSource(saying(-1), 1)
            .build()
            .setBalance(BalancePolicy.AGREEING_WEIGHT)
            .calculate(ANY_CANDLES);

        assertEquals(0.5, merged.confidence(), 1e-9);
        assertEquals(0.75, merged.positionBalance(), 1e-9);
    }

    /** Ровный раскол - встать в деньги: ответа нет, согласных с ним нет. */
    @Test
    void asksForNothingWhenTheCommitteeIsEvenlySplit() {
        Signal merged = new CompositeFactorSignalSource.Builder()
            .addSource(saying(1), 1)
            .addSource(saying(-1), 1)
            .build()
            .setBalance(BalancePolicy.AGREEING_WEIGHT)
            .calculate(ANY_CANDLES);

        assertEquals(0, merged.confidence(), 1e-9);
        assertEquals(0, merged.positionBalance(), 1e-9);
    }

    /** Молчащий делегат не голосует ни за, ни против, но и состав из-за него не меняет сторону. */
    @Test
    void countsSilenceAsNoVote() {
        Signal merged = new CompositeFactorSignalSource.Builder()
            .addSource(saying(1), 1)
            .addSource(candles -> Signal.NEUTRAL, 1)
            .build()
            .setBalance(BalancePolicy.AGREEING_WEIGHT)
            .calculate(ANY_CANDLES);

        assertEquals(0.5, merged.confidence(), 1e-9);
        assertEquals(0.5, merged.positionBalance(), 1e-9);
    }

    @Test
    void refusesPoliciesItCannotApply() {
        assertThrows(IllegalArgumentException.class, () -> committee().setType((TypePolicy) null));
        assertThrows(IllegalArgumentException.class, () -> committee().setType((SignalType) null));
        assertThrows(IllegalArgumentException.class, () -> committee().setBalance(null));
    }

    /** Трое за, двое против, веса равные. */
    private static CompositeFactorSignalSource committee() {
        return new CompositeFactorSignalSource.Builder()
            .addSource(saying(1), 1)
            .addSource(saying(1), 1)
            .addSource(saying(1), 1)
            .addSource(saying(-1), 1)
            .addSource(saying(-1), 1)
            .build();
    }

    private static CompositeFactorSignalSource bearishCommittee() {
        return new CompositeFactorSignalSource.Builder()
            .addSource(saying(-1), 1)
            .addSource(saying(-1), 1)
            .addSource(saying(1), 1)
            .build();
    }

    private static SignalSource saying(double confidence) {
        return candles -> new Signal(confidence, 1);
    }
}
