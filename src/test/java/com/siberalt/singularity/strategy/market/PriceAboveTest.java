package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.indicator.Sma;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Цена относительно уровня, где уровень - любая формула по последним свечам. */
class PriceAboveTest {
    @Test
    void comparesTheLastPriceWithTheLevel() {
        MarketCondition above = new PriceAbove(candles -> 100);

        assertTrue(above.holds(closing(90, 95, 101)));
        assertFalse(above.holds(closing(110, 105, 99)));
    }

    /** Ровно на уровне - «не выше»: граница принадлежит одной стороне, иначе цена и выше, и ниже сразу. */
    @Test
    void keepsTheBoundaryOnOneSide() {
        MarketCondition above = new PriceAbove(candles -> 100);

        assertFalse(above.holds(closing(100)));
        assertTrue(above.negated().holds(closing(100)));
    }

    /** Ниже - это отрицание того же условия, а не второй уровень: написанный дважды разошёлся бы. */
    @Test
    void readsBelowAsItsOwnNegation() {
        MarketCondition above = new PriceAbove(candles -> 100);

        assertTrue(above.negated().holds(closing(99)));
        assertFalse(above.negated().holds(closing(101)));
    }

    /** Формула любая - здесь настоящая SMA, и условие про неё ничего не знает. */
    @Test
    void takesWhateverFormulaItIsGiven() {
        MarketCondition aboveSma = new PriceAbove(candles -> Sma.of(candles, 3));

        // Средняя последних трёх - 101, закрытие 103.
        assertTrue(aboveSma.holds(closing(99, 100, 101, 102, 103)));
        assertFalse(aboveSma.holds(closing(103, 102, 101, 100, 99)));
    }

    /**
     * Пока уровня нет - средняя не набрала период - условие не выполнено. Пропускать всё, пока не
     * прогрелось, значит не иметь условия на самом нужном участке.
     */
    @Test
    void refusesWhileTheLevelIsNotThere() {
        MarketCondition aboveSma = new PriceAbove(candles -> Sma.of(candles, 50));

        assertFalse(aboveSma.holds(closing(100, 200, 300)));
        assertFalse(aboveSma.holds(null));
        assertFalse(aboveSma.holds(List.of()));
    }

    @Test
    void refusesWhatCannotBeCompared() {
        assertThrows(IllegalArgumentException.class, () -> new PriceAbove(null));
        assertThrows(IllegalArgumentException.class, () -> new PriceAbove(candles -> 1, null));
    }

    private static List<Candle> closing(double... closes) {
        List<Candle> candles = new ArrayList<>(closes.length);

        for (int at = 0; at < closes.length; at++) {
            candles.add(Candle.of(new TimePoint(at, null), closes[at]));
        }

        return candles;
    }
}
