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

/** Наклон текущей ноги: от последнего разворота до последнего бара, в волатильностях за бар. */
class PivotSlopeSignalSourceTest {
    private static final double THRESHOLD = 2;

    /** Нога вверх даёт плюс, нога вниз - минус. */
    @Test
    void readsTheDirectionOfTheCurrentLeg() {
        assertTrue(signalOf(legs(12, 3, 12, -3)).confidence() < 0, "последняя нога вниз");
        assertTrue(signalOf(legs(12, -3, 12, 3)).confidence() > 0, "последняя нога вверх");
    }

    /**
     * Крутизна сохраняется - ради этого в знаменателе волатильность, а не собственный ход окна.
     * Самонормировка сократила бы наклон со средним шагом, и обе ноги дали бы <b>одно</b> число.
     * <p>
     * Различие при этом куда меньше, чем девятикратное различие самих наклонов: {@code tanh} сжимает
     * хвост, а волатильность окна растёт вместе с ногой, так что и знаменатель прибавляет. Мера
     * отвечает «круто или полого», а не «во сколько раз круче».
     */
    @Test
    void keepsSteepnessInTheAnswer() {
        double gentle = signalOf(legs(12, -3, 14, 1)).confidence();
        double steep = signalOf(legs(12, -3, 14, 9)).confidence();

        assertTrue(steep > gentle, "крутая нога " + steep + " против пологой " + gentle);
    }

    /** Дёрганая нога закрывает источник: наклон там не слаб, а не определён. */
    @Test
    void saysNothingWhenTheLegIsNotALine() {
        List<Candle> candles = new ArrayList<>(legs(12, -3));

        for (int at = 0; at < 14; at++) {
            candles.add(candle(candles.size(), 100 + (at % 2 == 0 ? 0 : 8)));
        }

        assertEquals(Signal.NEUTRAL, signalOf(reindexed(candles)));
    }

    /** Пока разворота не нашлось, сказать нечего: окну неоткуда начаться. */
    @Test
    void saysNothingUntilAPivotIsConfirmed() {
        assertEquals(Signal.NEUTRAL, signalOf(legs(30, 1)), "прямой ход - ни одного разворота");
        assertEquals(Signal.NEUTRAL, signalOf(null));
        assertEquals(Signal.NEUTRAL, signalOf(legs(2, 1)));
    }

    /**
     * Нога короче трёх баров закрывает источник: прямую проводить не по чему. Разворот здесь
     * назначается напрямую, а не ищется зигзагом, - проверяется правило самого источника.
     */
    @Test
    void saysNothingWhileTheLegIsTooShort() {
        List<Candle> candles = legs(20, 3);

        assertEquals(Signal.NEUTRAL, new PivotSlopeSignalSource(
            window -> List.of(window.get(window.size() - 2)), 0.4).calculate(candles),
            "две точки - не нога");
        assertTrue(!Signal.NEUTRAL.equals(new PivotSlopeSignalSource(
            window -> List.of(window.get(window.size() - 3)), 0.4).calculate(candles)),
            "три бара - уже нога");
    }

    /** Та же бумага в сто раз дороже читается так же: наклон делится на волатильность. */
    @Test
    void answersTheSameWhateverThePriceLevel() {
        List<Candle> cheap = legs(12, -3, 14, 3);
        List<Candle> dear = new ArrayList<>();

        for (int at = 0; at < cheap.size(); at++) {
            dear.add(candle(at, cheap.get(at).getCloseAsDouble() * 100));
        }

        assertEquals(signalOf(cheap).confidence(), signalOf(dear).confidence(), 1e-9);
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class,
            () -> new PivotSlopeSignalSource(null, 0.4));
        assertThrows(IllegalArgumentException.class,
            () -> new PivotSlopeSignalSource(new ZigZagExtremeLocator(1), -0.1));
        assertThrows(IllegalArgumentException.class,
            () -> new PivotSlopeSignalSource(new ZigZagExtremeLocator(1), 1.5));
        assertThrows(IllegalArgumentException.class,
            () -> new PivotSlopeSignalSource(1).setVolatilityCalculator(null));
        assertThrows(IllegalArgumentException.class,
            () -> new PivotSlopeSignalSource(1).setPriceExtractor(null));
    }

    private static Signal signalOf(List<Candle> candles) {
        return new PivotSlopeSignalSource(THRESHOLD).calculate(candles);
    }

    /** Ход из отрезков заданной длины и наклона, сшитых без разрыва. */
    private static List<Candle> legs(double... lengthsAndSlopes) {
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

    /** Индексы обязаны расти: по ним ищется, где стоит разворот. */
    private static List<Candle> reindexed(List<Candle> candles) {
        List<Candle> fixed = new ArrayList<>(candles.size());

        for (int at = 0; at < candles.size(); at++) {
            fixed.add(candle(at, candles.get(at).getCloseAsDouble()));
        }

        return fixed;
    }

    private static Candle candle(int at, double price) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, 1);
    }
}
