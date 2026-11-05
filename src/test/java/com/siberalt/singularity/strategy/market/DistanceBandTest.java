package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.indicator.Sma;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Сторона по расстоянию от кривой с полосой. Кривая - прямая на уровне 100, волатильность - единица,
 * так что расстояние в волатильностях равно разности с сотней, и ожидания считаются в уме.
 */
class DistanceBandTest {
    private static final double WIDTH = 2;
    private static final double LEVEL = 100;

    @Test
    void readsAPriceFarAboveTheCurveAsRising() {
        assertEquals(TrendZone.UP, band().of(prices(103, 104)));
    }

    @Test
    void readsAPriceFarBelowTheCurveAsFalling() {
        assertEquals(TrendZone.DOWN, band().of(prices(97, 96)));
    }

    /**
     * Цена вышла из полосы вверх, потом ушла под кривую, но не дальше полосы: сторона прежняя. Ещё бар
     * за нижним краем - и сторона сменилась.
     */
    @Test
    void keepsTheSideWhileThePriceIsInsideTheBand() {
        assertEquals(TrendZone.UP, band().of(prices(103, 101, 99, 98.5)));
        assertEquals(TrendZone.DOWN, band().of(prices(103, 101, 99, 98.5, 97.5)));
    }

    /**
     * Залипание ограничено: сколько бы цена ни болталась внутри полосы под прежней стороной, выход за
     * противоположный край меняет её сразу и без условий.
     */
    @Test
    void cannotStayOnTheWrongSideBeyondTheBand() {
        assertEquals(TrendZone.UP, band().of(prices(96, 99, 101, 101.5, 102, 102.5)));
    }

    /** Пока цена ни разу не вышла из полосы, сторону засевает знак расстояния на первом читаемом баре. */
    @Test
    void seedsTheSideFromTheFirstReadableBar() {
        assertEquals(TrendZone.UP, band().of(prices(100.5, 99.5, 99)));
        assertEquals(TrendZone.DOWN, band().of(prices(99.5, 100.5, 101)));
    }

    /**
     * Средняя догоняет стоящую цену и не перехлёстывает её: расстояние сходится к нулю сверху, и
     * сторона не меняется. Это и есть дрейф, который пересечению приходилось отсекать наклоном.
     */
    @Test
    void doesNotTurnWhenTheCurveCatchesUpWithAStillPrice() {
        DistanceBand overSma = new DistanceBand(
            candles -> Sma.seriesOf(candles, 3, Candle::getCloseAsDouble), WIDTH)
            .setVolatilityCalculator(volatility(1));

        assertEquals(TrendZone.UP, overSma.of(prices(100, 104, 108, 112, 112, 112, 112, 112)));
    }

    /** Нулевая ширина - сторона цены без гистерезиса, контроль в той же сборке. */
    @Test
    void collapsesToTheSideOfThePriceAtZeroWidth() {
        DistanceBand none = new DistanceBand(flat(), 0).setVolatilityCalculator(volatility(1));

        assertEquals(TrendZone.DOWN, none.of(prices(103, 101, 99.5)));
        assertEquals(TrendZone.UP, none.of(prices(97, 99, 100.5)));
        assertEquals(TrendZone.DOWN, none.of(prices(99, 100)), "ровно на кривой - прежняя сторона");
    }

    /** Ширина в волатильностях: тот же ход при волатильности 10 из полосы не выходит. */
    @Test
    void measuresTheDistanceInVolatilities() {
        DistanceBand loud = new DistanceBand(flat(), WIDTH).setVolatilityCalculator(volatility(10));

        assertEquals(TrendZone.UP, loud.of(prices(103, 101, 99, 98.5, 97.5)));
    }

    @Test
    void reportsTheDistanceAtEveryBar() {
        DistanceBand overSma = new DistanceBand(
            candles -> Sma.seriesOf(candles, 2, Candle::getCloseAsDouble), WIDTH)
            .setVolatilityCalculator(volatility(2));
        double[] distances = overSma.distancesOf(prices(100, 104, 98));

        assertTrue(Double.isNaN(distances[0]), "средняя ещё не прогрелась");
        assertArrayEquals(new double[]{1, -1.5}, Arrays.copyOfRange(distances, 1, 3), 1e-9);
    }

    @Test
    void saysNothingWhileTheCurveIsNotReady() {
        DistanceBand overSma = new DistanceBand(
            candles -> Sma.seriesOf(candles, 50, Candle::getCloseAsDouble), WIDTH);

        assertTrue(Double.isNaN(overSma.of(prices(100, 101, 102))));
        assertTrue(Double.isNaN(band().of(List.of())));
        assertTrue(Double.isNaN(band().of(null)));
        assertTrue(Double.isNaN(band().of(prices(100, 100))), "ровно на кривой - ни стороны, ни знака");
    }

    /** Ответ - метка, а не измерение: иначе TrendZones её не примет. */
    @Test
    void answersWithALabelAndNothingElse() {
        for (double price : new double[]{94, 99.9, 100.1, 106}) {
            double reading = band().of(prices(price, price + 1, price - 3, price));

            assertTrue(reading == TrendZone.UP || reading == TrendZone.DOWN, "получено " + reading);
        }
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new DistanceBand(null, WIDTH));
        assertThrows(IllegalArgumentException.class, () -> new DistanceBand(flat(), -1));
        assertThrows(IllegalArgumentException.class, () -> new DistanceBand(flat(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> band().setPriceExtractor(null));
        assertThrows(IllegalArgumentException.class, () -> band().setVolatilityCalculator(null));
    }

    private static DistanceBand band() {
        return new DistanceBand(flat(), WIDTH).setVolatilityCalculator(volatility(1));
    }

    private static VolatilityCalculator volatility(double value) {
        return candles -> value;
    }

    private static MarketSeries flat() {
        return candles -> {
            double[] curve = new double[candles.size()];

            Arrays.fill(curve, LEVEL);

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
