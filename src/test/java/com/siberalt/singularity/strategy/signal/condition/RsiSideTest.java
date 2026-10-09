package com.siberalt.singularity.strategy.signal.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Покупать внизу, продавать наверху: один и тот же RSI пропускает одну сторону и запрещает другую. */
class RsiSideTest {
    private static final Signal BUY = new Signal(1, 1);
    private static final Signal SELL = new Signal(-1, 1);

    /** Непрерывное падение читается как RSI = 0: лонг разрешён, шорт нет. */
    @Test
    void letsTheLongThroughAtTheBottom() {
        RsiSide condition = new RsiSide(14);

        assertTrue(condition.holds(falling(20), () -> BUY));
        assertFalse(condition.holds(falling(20), () -> SELL));
    }

    /** Непрерывный рост - RSI = 100, и всё наоборот. */
    @Test
    void letsTheShortThroughAtTheTop() {
        RsiSide condition = new RsiSide(14);

        assertFalse(condition.holds(rising(20), () -> BUY));
        assertTrue(condition.holds(rising(20), () -> SELL));
    }

    /**
     * Уровни задаются по сторонам отдельно, и сами решают, что чем является. {@code (100, 0)} пропускает
     * всё - фильтра нет; {@code (0, 100)} не пропускает ничего, кроме крайностей, и на рост с RSI = 100
     * лонг уже не даёт.
     */
    @Test
    void takesALevelForEachSide() {
        assertTrue(new RsiSide(14, 100, 0).holds(rising(20), () -> BUY));
        assertTrue(new RsiSide(14, 100, 0).holds(falling(20), () -> SELL));
        assertFalse(new RsiSide(14, 0, 100).holds(rising(20), () -> BUY));
        assertFalse(new RsiSide(14, 0, 100).holds(falling(20), () -> SELL));
    }

    /** Нет стороны - нечего запрещать; наружу всё равно уйдёт молчание делегата. */
    @Test
    void hasNothingToSayAboutSilence() {
        assertTrue(new RsiSide(14).holds(rising(20), () -> Signal.NEUTRAL));
        assertTrue(new RsiSide(14).holds(rising(20), () -> null));
    }

    /**
     * Пока RSI не набрал период, условие не выполнено. Пропускать всё в этот момент значит не иметь фильтра
     * там, где он нужнее всего - на первых барах.
     */
    @Test
    void refusesWhileThereIsNoReading() {
        assertFalse(new RsiSide(14).holds(falling(5), () -> BUY));
        assertFalse(new RsiSide(14).holds(falling(5), () -> SELL));
    }

    @Test
    void refusesWhatCannotBeChecked() {
        assertThrows(IllegalArgumentException.class, () -> new RsiSide(0));
        assertThrows(IllegalArgumentException.class, () -> new RsiSide(14, 101, 50));
        assertThrows(IllegalArgumentException.class, () -> new RsiSide(14, 50, -1));
        assertThrows(IllegalArgumentException.class, () -> new RsiSide(14, Double.NaN, 50));
    }

    private static List<Candle> rising(int count) {
        return seriesOf(count, at -> 100 + 2.0 * at);
    }

    private static List<Candle> falling(int count) {
        return seriesOf(count, at -> 500 - 2.0 * at);
    }

    private static List<Candle> seriesOf(int count, IntToDoubleFunction price) {
        List<Candle> candles = new ArrayList<>(count);

        for (int at = 0; at < count; at++) {
            candles.add(Candle.of(TimePoint.NULL, price.applyAsDouble(at)));
        }

        return candles;
    }
}
