package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Якорь на разворотах: ранняя нога между разворотами, поздняя - от последнего до конца окна. */
class PivotStretchPolicyTest {
    private static final double THRESHOLD = 2;

    /**
     * Соседние ноги идут в разные стороны, и мера равна ровно пределу. Это не особенность данных, а
     * алгебра: при разных знаках {@code |sl − sw| = |sl| + |sw|}. Тест стоит первым, потому что это
     * свойство конструкции, которое надо знать до того, как ставить источник в сборку.
     */
    @Test
    void readsAdjacentLegsAsTheLimitBecauseTheyAlwaysDisagree() {
        assertEquals(1, signalOf(zigzag(12, 1, 12, -1, 12, 1)).confidence(), 1e-12,
            "последняя нога вверх - предел вверх");
        assertEquals(-1, signalOf(zigzag(12, -1, 12, 1, 12, -1)).confidence(), 1e-12,
            "последняя нога вниз - предел вниз");
    }

    /** И крутизна ног на ответ не влияет - ещё одно следствие того же вырождения. */
    @Test
    void answersTheSameWhateverTheLegsSteepness() {
        assertEquals(signalOf(zigzag(12, 1, 12, -1, 12, 1)).confidence(),
            signalOf(zigzag(12, 1, 12, -5, 12, 9)).confidence(), 1e-12);
    }

    /** Пока разворотов меньше двух, сказать нечего: одной ноги для сравнения не хватает. */
    @Test
    void saysNothingUntilTwoPivotsAreConfirmed() {
        assertEquals(Signal.NEUTRAL, signalOf(straight(40, 1)), "прямой ход - ни одного разворота");
        assertEquals(Signal.NEUTRAL, signalOf(zigzag(12, 1, 12, -1)), "один разворот - всё ещё мало");
        assertEquals(Signal.NEUTRAL, signalOf(null));
        assertEquals(Signal.NEUTRAL, signalOf(straight(4, 1)));
    }

    /**
     * Короткая поздняя нога закрывает источник: регрессию по двум точкам провести можно, но сравнивать
     * её с чем-либо бессмысленно. На реальных данных при нулевом пороге так выходит на 76% баров.
     */
    @Test
    void saysNothingWhileTheLateLegIsTooShort() {
        List<Candle> barelyTurned = new ArrayList<>(zigzag(12, 1, 12, -1, 12, 1));

        barelyTurned.add(candle(barelyTurned.size(), 1));

        assertEquals(Signal.NEUTRAL,
            new SlopeChangeSignalSource(
                StretchPolicy.betweenPivots(new ZigZagExtremeLocator(THRESHOLD)), 20).calculate(barelyTurned),
            "ноге нужно двадцать баров, а их столько нет");
    }

    /** Порог решает, что считать разворотом: при высоком мелкие колебания его не образуют. */
    @Test
    void takesTheThresholdAsGiven() {
        List<Candle> shallow = zigzag(12, 3, 4, -1, 12, 3);

        assertTrue(Math.abs(anchored(0.5).calculate(shallow).confidence()) > 0,
            "низкий порог видит неглубокий откат разворотом");
        assertEquals(Signal.NEUTRAL, anchored(20).calculate(shallow),
            "высокий порог не видит в нём ничего");
    }

    /** Сила - наименьшее из двух R², как и у меры с фиксированным окном. */
    @Test
    void putsTheWorseFitIntoStrength() {
        assertEquals(1, signalOf(zigzag(12, 1, 12, -1, 12, 1)).strength(), 1e-9,
            "прямые ноги - полная сила");
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new ZigZagExtremeLocator(0));
        assertThrows(IllegalArgumentException.class, () -> new ZigZagExtremeLocator(-1));
        assertThrows(IllegalArgumentException.class, () -> StretchPolicy.betweenPivots(null));
        assertThrows(IllegalArgumentException.class,
            () -> StretchPolicy.betweenPivots(new ZigZagExtremeLocator(), 1));
        assertThrows(IllegalArgumentException.class,
            () -> new ZigZagExtremeLocator().setPriceExtractor(null));
    }

    private static Signal signalOf(List<Candle> candles) {
        return anchored(THRESHOLD).calculate(candles);
    }

    private static SlopeChangeSignalSource anchored(double threshold) {
        return new SlopeChangeSignalSource(
            StretchPolicy.betweenPivots(new ZigZagExtremeLocator(threshold)));
    }

    /** Ход из отрезков заданной длины и наклона, сшитых без разрыва. */
    private static List<Candle> zigzag(double... lengthsAndSlopes) {
        List<Candle> candles = new ArrayList<>();
        double price = 1000;

        for (int leg = 0; leg < lengthsAndSlopes.length; leg += 2) {
            for (int at = 0; at < (int) lengthsAndSlopes[leg]; at++) {
                candles.add(candle(candles.size(), price));
                price += lengthsAndSlopes[leg + 1];
            }
        }

        return candles;
    }

    private static List<Candle> straight(int bars, double step) {
        return zigzag(bars, step);
    }

    private static Candle candle(int at, double price) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, 1);
    }
}
