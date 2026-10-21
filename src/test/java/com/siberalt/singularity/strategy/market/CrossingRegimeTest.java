package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Режим по частоте пересечений: гроздь - флэт, одинокое пересечение - сторона. */
class CrossingRegimeTest {
    private static final int WINDOW = 10;
    private static final int CROWD = 2;
    private static final double LEVEL = 100;

    /** Цена всё окно выше кривой: пересечений нет вовсе, сторона восходящая. */
    @Test
    void readsAPriceHeldAboveTheCurveAsRising() {
        assertEquals(TrendZone.UP, regime().of(prices(102, 103, 104, 105, 106, 107)));
    }

    @Test
    void readsAPriceHeldBelowTheCurveAsFalling() {
        assertEquals(TrendZone.DOWN, regime().of(prices(98, 97, 96, 95, 94, 93)));
    }

    /** Одно пересечение гроздью не является: сторона та, на которой цена оказалась. */
    @Test
    void readsASingleCrossingAsTheSideItLandedOn() {
        assertEquals(TrendZone.UP, regime().of(prices(97, 98, 99, 101, 102, 103)));
    }

    /** Два пересечения в окрестности - уже гроздь, то есть флэт. */
    @Test
    void readsTwoCrossingsInTheNeighbourhoodAsFlat() {
        assertEquals(TrendZone.FLAT, regime().of(prices(98, 102, 98, 99)));
    }

    /**
     * Та же дёрганая история, но окрестность короче пересечений: старые в счёт не идут, и режим
     * снова получает сторону. Окно здесь и есть вся задержка меры.
     */
    @Test
    void forgetsCrossingsOlderThanTheNeighbourhood() {
        CrossingRegime narrow = new CrossingRegime(flat(), 3, CROWD);

        assertEquals(TrendZone.UP, narrow.of(prices(98, 102, 98, 101, 102, 103)));
    }

    /** Порог грозди поднимается: три пересечения терпятся, четыре нет. */
    @Test
    void takesTheCrowdThresholdAsGiven() {
        CrossingRegime patient = new CrossingRegime(flat(), WINDOW, 4);

        assertEquals(TrendZone.UP, patient.of(prices(98, 102, 98, 102)), "три пересечения - терпим");
        assertEquals(TrendZone.FLAT, patient.of(prices(98, 102, 98, 102, 98)), "четыре - гроздь");
    }

    /**
     * Цена ровно на кривой стороной не считается. Иначе касание давало бы два пересечения там, где
     * цена всего лишь дотронулась и пошла прежним путём.
     */
    @Test
    void doesNotCountATouchAsTwoCrossings() {
        assertEquals(TrendZone.UP, regime().of(prices(102, 103, 100, 104, 105)));
    }

    /** Пока кривая не прогрелась, ответа нет - и это не флэт. */
    @Test
    void saysNothingWhileTheCurveIsNotReady() {
        CrossingRegime overSma = new CrossingRegime(
            candles -> com.siberalt.singularity.strategy.indicator.Sma.seriesOf(candles, 50,
                Candle::getCloseAsDouble), WINDOW, CROWD);

        assertTrue(Double.isNaN(overSma.of(prices(100, 101, 102))));
        assertTrue(Double.isNaN(regime().of(List.of())));
        assertTrue(Double.isNaN(regime().of(null)));
    }

    /** Ответ - метка, а не измерение: только три значения, иначе TrendZones её не примет. */
    @Test
    void answersWithALabelAndNothingElse() {
        for (double price : new double[]{94, 100, 106}) {
            double reading = regime().of(prices(price, price + 1, price - 1, price));

            assertTrue(reading == TrendZone.UP || reading == TrendZone.FLAT
                || reading == TrendZone.DOWN, "получено " + reading);
        }
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new CrossingRegime(null, WINDOW, CROWD));
        assertThrows(IllegalArgumentException.class, () -> new CrossingRegime(flat(), 1, CROWD));
        assertThrows(IllegalArgumentException.class, () -> new CrossingRegime(flat(), WINDOW, 1));
        assertThrows(IllegalArgumentException.class, () -> regime().setPriceExtractor(null));
    }

    private static CrossingRegime regime() {
        return new CrossingRegime(flat(), WINDOW, CROWD);
    }

    /** Кривая-прямая на уровне 100: пересечения считаются с ней, а не со средней от самой цены. */
    private static MarketSeries flat() {
        return candles -> {
            double[] curve = new double[candles.size()];

            java.util.Arrays.fill(curve, LEVEL);

            return curve;
        };
    }

    private static List<Candle> prices(double... prices) {
        List<Candle> candles = new ArrayList<>(prices.length);

        for (int at = 0; at < prices.length; at++) {
            Quotation value = Quotation.of(prices[at]);

            candles.add(new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
                value, value, value, value, 1));
        }

        return candles;
    }
}
