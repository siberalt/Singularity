package com.siberalt.singularity.strategy.signal.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Мёртвая зона: сторона сигнала значит что-то только когда сила дошла до порога. */
class DeadbandTest {
    private static final List<Candle> ANY_CANDLES = List.of(Candle.of(TimePoint.NULL, 100.0));

    @Test
    void passesWhatIsFarEnoughFromZero() {
        assertTrue(new Deadband(0.002).holds(ANY_CANDLES, () -> new Signal(1, 0.003)));
        assertFalse(new Deadband(0.002).holds(ANY_CANDLES, () -> new Signal(1, 0.001)));
    }

    /** Порог включительно: иначе ровно пограничное значение зависело бы от последнего бита. */
    @Test
    void countsTheThresholdItself() {
        assertTrue(new Deadband(0.002).holds(ANY_CANDLES, () -> new Signal(1, 0.002)));
    }

    /** Зона не сторона: слабый шорт отсекается так же, как слабый лонг. */
    @Test
    void cutsBothSidesAlike() {
        assertFalse(new Deadband(0.002).holds(ANY_CANDLES, () -> new Signal(-1, 0.001)));
        assertTrue(new Deadband(0.002).holds(ANY_CANDLES, () -> new Signal(-1, 0.003)));
    }

    /** Молчание делегата - нулевая сила, и оно отсекается само, без отдельной проверки. */
    @Test
    void refusesSilence() {
        assertFalse(new Deadband(0.002).holds(ANY_CANDLES, () -> Signal.NEUTRAL));
        assertFalse(new Deadband(0.002).holds(ANY_CANDLES, () -> null));
    }

    @Test
    void refusesAZoneOfNoWidth() {
        assertThrows(IllegalArgumentException.class, () -> new Deadband(0));
        assertThrows(IllegalArgumentException.class, () -> new Deadband(-0.001));
        assertThrows(IllegalArgumentException.class, () -> new Deadband(Double.NaN));
    }
}
