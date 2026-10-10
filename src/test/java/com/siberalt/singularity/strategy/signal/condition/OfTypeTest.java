package com.siberalt.singularity.strategy.signal.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.market.MarketCondition;
import com.siberalt.singularity.strategy.market.PriceAbove;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalCondition;
import com.siberalt.singularity.strategy.signal.SignalType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Пропускать только названные типы - и то, ради чего это нужно. */
class OfTypeTest {
    private static final List<Candle> ANY_CANDLES = List.of(Candle.of(TimePoint.NULL, 100.0));

    @Test
    void passesOnlyTheTypesItWasGiven() {
        SignalCondition profitOnly = new OfType(SignalType.TAKE_PROFIT);

        assertTrue(profitOnly.holds(ANY_CANDLES, () -> typed(SignalType.TAKE_PROFIT)));
        assertFalse(profitOnly.holds(ANY_CANDLES, () -> typed(SignalType.STOP_LOSS)));
        assertFalse(profitOnly.holds(ANY_CANDLES, () -> typed(SignalType.POSITION_EXIT)));
        assertFalse(profitOnly.holds(ANY_CANDLES, () -> Signal.NEUTRAL));
    }

    @Test
    void takesSeveralTypesAtOnce() {
        SignalCondition closing = new OfType(SignalType.TAKE_PROFIT, SignalType.STOP_LOSS);

        assertTrue(closing.holds(ANY_CANDLES, () -> typed(SignalType.TAKE_PROFIT)));
        assertTrue(closing.holds(ANY_CANDLES, () -> typed(SignalType.STOP_LOSS)));
        assertFalse(closing.holds(ANY_CANDLES, () -> typed(SignalType.POSITION_EXIT)));
    }

    /**
     * То самое правило, ради которого тип и нужен: выше средней пропускаем только фиксацию прибыли, ниже -
     * что угодно. Через {@code and} такое не собрать - там оба условия обязательны, а здесь они подменяют
     * друг друга.
     */
    @Test
    void lettingOnlyProfitTakingThroughAboveTheLevel() {
        MarketCondition above = new PriceAbove(candles -> 100);
        SignalCondition rule = new OfType(SignalType.TAKE_PROFIT)
            .or(SignalCondition.of(above.negated()));

        assertTrue(rule.holds(closing(101), () -> typed(SignalType.TAKE_PROFIT)),
            "выше уровня фиксация прибыли проходит");
        assertFalse(rule.holds(closing(101), () -> typed(SignalType.POSITION_EXIT)),
            "выше уровня обычный выход не проходит");
        assertTrue(rule.holds(closing(99), () -> typed(SignalType.POSITION_EXIT)),
            "ниже уровня проходит любой выход");
    }

    @Test
    void refusesAListOfNothing() {
        assertThrows(IllegalArgumentException.class, OfType::new);
        assertThrows(IllegalArgumentException.class, () -> new OfType((SignalType) null));
        assertThrows(IllegalArgumentException.class,
            () -> new OfType(SignalType.TAKE_PROFIT).or(null));
    }

    private static Signal typed(SignalType type) {
        return new Signal(-1, 1).withType(type);
    }

    private static List<Candle> closing(double... closes) {
        List<Candle> candles = new ArrayList<>(closes.length);

        for (int at = 0; at < closes.length; at++) {
            candles.add(Candle.of(new TimePoint(at, null), closes[at]));
        }

        return candles;
    }
}
