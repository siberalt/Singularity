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

    /** Мера по одному бару - расстояние последнего; окна нет - и мерить нечего. */
    @Test
    void readsTheDistanceOfTheLastBar() {
        assertEquals(3, band().lastDistanceOf(prices(101, 103)), 1e-9);
        assertEquals(-2.5, band().lastDistanceOf(prices(101, 97.5)), 1e-9);
        assertTrue(Double.isNaN(band().lastDistanceOf(List.of())));
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

    /**
     * Резкое падение внутри полосы: статическая держит прежнюю сторону, сужение - нет. Наклон по трём
     * последним закрытиям 102, 100.5, 99 - минус 1.5 за бар, край вниз 2 − 1.5 = 0.5, а расстояние −1.
     */
    @Test
    void turnsEarlierOnASteepMove() {
        List<Candle> fall = prices(103, 102, 100.5, 99);

        assertEquals(TrendZone.UP, band().of(fall));
        assertEquals(TrendZone.DOWN, narrowing(1, 0.5).of(fall));
        assertEquals(0.5, narrowing(1, 0.5).edgesOf(fall).down()[3], 1e-9);
    }

    /**
     * Медленный подход по 0.8 за бар сужает край вниз только до 1.2, и расстояние −1 внутри него; потом
     * цена встаёт под кривой - наклона нет, край снова 2. Сторона прежняя всё время.
     */
    @Test
    void keepsTheSideOfASlowApproachAndAStillPrice() {
        List<Candle> slow = prices(103, 102.2, 101.4, 100.6, 99.8, 99, 99, 99);
        DistanceBand.Edges edges = narrowing(1, 0.5).edgesOf(slow);

        assertEquals(TrendZone.UP, narrowing(1, 0.5).of(slow));
        assertEquals(1.2, edges.down()[5], 1e-9);
        assertEquals(WIDTH, edges.down()[7], 1e-9);
    }

    /** Нулевой горизонт - та же статическая полоса, на любом ряду. */
    @Test
    void equalsTheStaticBandAtZeroHorizon() {
        for (List<Candle> candles : List.of(prices(103, 102, 100.5, 99), prices(103, 101, 99, 98.5, 97.5),
            prices(96, 99, 101, 101.5, 102, 102.5))) {
            assertEquals(band().of(candles), narrowing(0, 0.5).of(candles));
        }
    }

    /**
     * Края зажаты: обвал в 1.7 за бар при горизонте 10 сузил бы край в минус, а он остаётся на нижнем
     * пределе 0.5 - и расстояние −0.4 внутри него сторону не меняет.
     */
    @Test
    void doesNotNarrowBelowTheMinimum() {
        List<Candle> crash = prices(103, 103, 99.6);
        DistanceBand steep = narrowing(10, 0.5);

        assertEquals(0.5, steep.edgesOf(crash).down()[2], 1e-9);
        assertEquals(TrendZone.UP, steep.of(crash));
    }

    /** Ход против выхода полосу не расширяет: граница залипания остаётся прежней. */
    @Test
    void neverWidensBeyondTheWidth() {
        DistanceBand.Edges edges = narrowing(1, 0.5).edgesOf(prices(100, 98, 96, 94));

        assertEquals(WIDTH, edges.up()[3], 1e-9);
        assertEquals(0.5, edges.down()[3], 1e-9);
    }

    @Test
    void reportsStaticEdgesWithoutNarrowing() {
        DistanceBand.Edges edges = band().edgesOf(prices(100, 98, 96, 94));

        assertArrayEquals(new double[]{WIDTH, WIDTH, WIDTH, WIDTH}, edges.up(), 1e-9);
        assertArrayEquals(new double[]{WIDTH, WIDTH, WIDTH, WIDTH}, edges.down(), 1e-9);
    }

    @Test
    void refusesANarrowingThatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> band().setSlopeNarrowing(0, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> band().setSlopeNarrowing(2, -1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> band().setSlopeNarrowing(2, Double.NaN, 0.5));
        assertThrows(IllegalArgumentException.class, () -> band().setSlopeNarrowing(2, 1, -0.1));
        assertThrows(IllegalArgumentException.class, () -> band().setSlopeNarrowing(2, 1, WIDTH + 0.1));
    }

    /**
     * Цена отбила кривую рывком - наклон по 97, 98, 100.5 равен 1.75 за бар, - но до края 2 не дошла:
     * статическая полоса держит сторону вниз, отмена схлопывает край и переходит вверх.
     */
    @Test
    void turnsAtTheCurveOnADecisiveReclaim() {
        List<Candle> push = prices(97, 98, 100.5, 102);

        assertEquals(TrendZone.DOWN, band().of(push));
        assertEquals(TrendZone.UP, reclaiming(1).of(push));
    }

    @Test
    void turnsAtTheCurveInTheMirrorCase() {
        List<Candle> drop = prices(103, 102, 99.5, 98);

        assertEquals(TrendZone.UP, band().of(drop));
        assertEquals(TrendZone.DOWN, reclaiming(1).of(drop));
    }

    /** Над кривой, но подход по 0.4 за бар: это не отбитая средняя, сторона прежняя. */
    @Test
    void keepsTheSideOnASlowReturn() {
        assertEquals(TrendZone.DOWN, reclaiming(1).of(prices(99, 99.4, 99.8, 100.2, 100.6)));
    }

    /** Кривая сама сползла под стоящую цену: цена над ней, но наклона цены нет - сторона прежняя. */
    @Test
    void keepsTheSideWhenTheCurveSlidesUnderAStillPrice() {
        MarketSeries sliding = candles -> {
            double[] curve = new double[candles.size()];

            for (int at = 0; at < curve.length; at++) {
                curve[at] = 101 - 0.5 * at;
            }

            return curve;
        };
        DistanceBand overSliding = new DistanceBand(sliding, WIDTH).setVolatilityCalculator(volatility(1))
            .setSlopeNarrowing(2, 0, WIDTH).setReclaim(1);

        assertEquals(TrendZone.DOWN, overSliding.of(prices(100, 100, 100, 100, 100)));
    }

    /**
     * Отбитая сторона держится как любая другая: цена сползает по 0.5 за бар обратно на кривую и под неё,
     * но до края −2 не доходит - сторона вверх.
     */
    @Test
    void holdsTheReclaimedSideUntilTheOppositeEdge() {
        assertEquals(TrendZone.UP,
            reclaiming(1).of(prices(97, 98, 100.5, 102, 101.5, 101, 100.5, 100, 99.5)));
    }

    /** Обратный рывок сквозь кривую - 102, 100, 98.5, минус 1.75 за бар - отбивает её уже вниз. */
    @Test
    void turnsBackOnADecisiveFallThroughTheCurve() {
        assertEquals(TrendZone.DOWN, reclaiming(1).of(prices(97, 98, 100.5, 102, 100, 98.5)));
    }

    /** Нулевой порог: почти любой возврат за кривую меняет сторону, ответ близок к стороне цены. */
    @Test
    void approachesThePriceSideAtZeroThreshold() {
        assertEquals(TrendZone.UP, reclaiming(0).of(prices(99, 99.4, 99.8, 100.2, 100.6)));
    }

    /**
     * Край схлопывается ниже нижнего предела сужения: на 100.5 наклон 1.75, сужение дало бы
     * 2 − 0.5·1.75 = 1.125, отмена даёт 0. Противоположный край не трогается; где наклона ещё нет - ширина.
     */
    @Test
    void reportsTheCollapsedEdge() {
        DistanceBand.Edges edges = narrowing(0.5, 1).setReclaim(1).edgesOf(prices(97, 98, 100.5, 102));

        assertEquals(WIDTH, edges.up()[1], 1e-9);
        assertEquals(0, edges.up()[2], 1e-9);
        assertEquals(WIDTH, edges.down()[2], 1e-9);
        assertEquals(1.125, narrowing(0.5, 1).edgesOf(prices(97, 98, 100.5, 102)).up()[2], 1e-9);
    }

    @Test
    void refusesAReclaimThatCannotBeAssembled() {
        assertThrows(IllegalStateException.class, () -> band().setReclaim(1));
        assertThrows(IllegalArgumentException.class, () -> narrowing(1, 0.5).setReclaim(-1));
        assertThrows(IllegalArgumentException.class, () -> narrowing(1, 0.5).setReclaim(Double.NaN));
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

    /** Наклон по двум приращениям, края не уже {@code minWidth}. */
    private static DistanceBand narrowing(double horizon, double minWidth) {
        return band().setSlopeNarrowing(2, horizon, minWidth);
    }

    /** Отмена без проекции: наклон по двум приращениям, края неподвижны, пока наклон не решительный. */
    private static DistanceBand reclaiming(double decisive) {
        return band().setSlopeNarrowing(2, 0, WIDTH).setReclaim(decisive);
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
