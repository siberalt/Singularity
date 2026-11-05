package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;
import com.siberalt.singularity.strategy.indicator.Sma;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пересечение считается сменой тренда только при решительном наклоне.
 * <p>
 * Кривая всюду - прямая на уровне 100, волатильность - единица: тогда порог {@code decisive} читается
 * прямо в единицах цены за бар, и ожидания в тестах можно посчитать в уме. Наклон идёт по двум
 * приращениям, то есть регрессией по трём точкам.
 */
class DecisiveCrossingTest {
    private static final int SLOPE_BARS = 2;
    private static final double DECISIVE = 1;
    private static final double LEVEL = 100;

    /**
     * Цена проваливается сквозь кривую: наклон по трём последним точкам около -4.5 за бар, порог
     * единица - подтверждено, тренд сменился.
     */
    @Test
    void takesASteepBreakDownAsAChangeOfTrend() {
        assertEquals(TrendZone.DOWN, crossing().of(prices(105, 104, 103, 95)));
    }

    /** Зеркально: провал снизу вверх при том же наклоне подтверждается так же. */
    @Test
    void takesASteepBreakUpAsAChangeOfTrend() {
        assertEquals(TrendZone.UP, crossing().of(prices(95, 96, 97, 105)));
    }

    /**
     * Та же сторона, но кривая всего лишь всплыла над ценой: наклон около -0.35 за бар, порога не
     * набрал - и прежний тренд остаётся, хотя цена уже под кривой.
     */
    @Test
    void doesNotTakeAWeakDriftDownAsAChangeOfTrend() {
        assertEquals(TrendZone.UP, crossing().of(prices(101, 100.5, 100.2, 99.8)));
    }

    @Test
    void doesNotTakeAWeakDriftUpAsAChangeOfTrend() {
        assertEquals(TrendZone.DOWN, crossing().of(prices(99, 99.5, 99.8, 100.2)));
    }

    /**
     * Пересечение держится несколько баров: пока наклон вялый, сторона прежняя, и меняется она на том
     * баре, где наклон наконец стал решительным. Подтверждение - не вспышка одного бара.
     */
    @Test
    void confirmsAStandingDisagreementOnTheBarTheSlopeTurnsDecisive() {
        double[] slow = {105, 100.4, 100.2, 99.8, 99.6};

        assertEquals(TrendZone.UP, crossing().of(prices(slow)),
            "цена под кривой два бара, но наклон -0.3 - не смена");
        assertEquals(TrendZone.DOWN, crossing().of(prices(105, 100.4, 100.2, 99.8, 99.6, 93)),
            "обвал на шестом баре наклон дал, разногласие закрылось");
    }

    /**
     * Наклон против стороны пересечения не подтверждает его ни при каком пороге: при {@code decisive}
     * равном нулю остаётся требование согласного знака, и оно одно отвергает этот бар.
     */
    @Test
    void neverConfirmsACrossingTheSlopeContradicts() {
        DecisiveCrossing anySlope = new DecisiveCrossing(flat(), SLOPE_BARS, 0)
            .setVolatilityCalculator(volatility(1));

        assertEquals(TrendZone.UP, anySlope.of(prices(90, 95, 101, 99)),
            "цена ушла под кривую, но наклон по трём последним точкам +2 за бар");
    }

    /**
     * Шкала наклона - волатильность, поэтому порог безразмерен. Тот же ход при волатильности 10 вместо
     * единицы решительным уже не считается.
     */
    @Test
    void readsTheSlopeInVolatilities() {
        DecisiveCrossing loud = new DecisiveCrossing(flat(), SLOPE_BARS, DECISIVE)
            .setVolatilityCalculator(volatility(10));

        assertEquals(TrendZone.UP, loud.of(prices(105, 104, 103, 95)));
    }

    /**
     * Наклон можно читать не по цене, а по кривой. У плоской кривой наклон нулевой, и тогда не
     * подтверждается ничего - это и есть измеримая слабость такого выбора: отстающая кривая отвергает
     * свежий пробой.
     */
    @Test
    void takesTheSlopeFromTheGivenSeries() {
        DecisiveCrossing overCurve = new DecisiveCrossing(flat(), SLOPE_BARS, DECISIVE)
            .setVolatilityCalculator(volatility(1))
            .setSlopeSeries(flat());

        assertEquals(TrendZone.UP, overCurve.of(prices(105, 104, 103, 95)));
    }

    /**
     * Вершина 107 за два бара до пересечения. Окно в четыре приращения ложится поперёк неё, захватывает
     * подъём и даёт наклон -1 - порога 1.5 не хватает. Нога от вершины - это 107, 103, 99, наклон -4.
     */
    @Test
    void anchorsTheSlopeOnTheLastTurnInsteadOfStraddlingIt() {
        List<Candle> breakAfterTop = prices(101, 103, 105, 107, 103, 99);

        assertEquals(TrendZone.UP, steep(4).of(breakAfterTop), "окно поперёк вершины");
        assertEquals(TrendZone.DOWN, steep(4).setSlopeAnchor(zigzag()).of(breakAfterTop),
            "нога от вершины");
    }

    /**
     * Вершина далеко, восемнадцать баров медленного сползания по 0.5, потом обвал сквозь кривую. Нога
     * целиком даёт наклон около -0.7 - обвал в ней растворился. С потолком в четыре приращения в окне
     * только хвост, и наклон -2.75.
     */
    @Test
    void capsTheLegSoALateAccelerationIsNotDiluted() {
        double[] slide = new double[21];

        slide[0] = 110;
        slide[1] = 112;

        for (int at = 2; at <= 18; at++) {
            slide[at] = 114 - 0.5 * (at - 2);
        }

        slide[19] = 101;
        slide[20] = 96;

        assertEquals(TrendZone.UP, steep(1000).setSlopeAnchor(zigzag()).of(prices(slide)),
            "нога без потолка");
        assertEquals(TrendZone.DOWN, steep(4).setSlopeAnchor(zigzag()).of(prices(slide)),
            "нога с потолком");
    }

    /**
     * Пересечение на баре сразу после вершины: нога в одно приращение не меряется, пересечение ждёт.
     * Ещё бар - и нога 107, 98, 97 его подтверждает.
     */
    @Test
    void waitsUntilTheLegHasTwoIncrements() {
        DecisiveCrossing anchored = crossing().setSlopeAnchor(zigzag());

        assertEquals(TrendZone.UP, anchored.of(prices(101, 103, 107, 98)));
        assertEquals(TrendZone.DOWN, anchored.of(prices(101, 103, 107, 98, 97)));
    }

    /** Контроль без подтверждения отдаёт сторону цены сразу - в этом вся разница с гистерезисом. */
    @Test
    void differsFromTheUnconfirmedControl() {
        List<Candle> drift = prices(101, 100.5, 100.2, 99.8);

        assertEquals(TrendZone.UP, crossing().of(drift));
        assertEquals(TrendZone.DOWN, DecisiveCrossing.naive(flat()).of(drift));
    }

    /** Первый читаемый бар засевает сторону, поэтому мёртвого участка сверх прогрева кривой нет. */
    @Test
    void seedsTheSideFromTheFirstReadableBar() {
        assertEquals(TrendZone.UP, crossing().of(prices(102, 103)));
        assertEquals(TrendZone.DOWN, crossing().of(prices(98, 97)));
    }

    /** Пока кривая не прогрелась, ответа нет - и это не флэт. */
    @Test
    void saysNothingWhileTheCurveIsNotReady() {
        DecisiveCrossing overSma = new DecisiveCrossing(
            candles -> Sma.seriesOf(candles, 50, Candle::getCloseAsDouble), SLOPE_BARS, DECISIVE);

        assertTrue(Double.isNaN(overSma.of(prices(100, 101, 102))));
        assertTrue(Double.isNaN(crossing().of(List.of())));
        assertTrue(Double.isNaN(crossing().of(null)));
        assertTrue(Double.isNaN(DecisiveCrossing.naive(flat()).of(List.of())));
    }

    /** Цена ровно на кривой стороной не считается: засевать по ней нечем. */
    @Test
    void doesNotTakeAPriceOnTheCurveAsASide() {
        assertEquals(TrendZone.DOWN, crossing().of(prices(100, 100, 98)));
        assertTrue(Double.isNaN(crossing().of(prices(100, 100, 100))));
    }

    /** Ответ - метка, а не измерение: иначе TrendZones её не примет. */
    @Test
    void answersWithALabelAndNothingElse() {
        for (double price : new double[]{94, 99.9, 100.1, 106}) {
            double reading = crossing().of(prices(price, price + 1, price - 1, price));

            assertTrue(reading == TrendZone.UP || reading == TrendZone.DOWN, "получено " + reading);
        }
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class,
            () -> new DecisiveCrossing(null, SLOPE_BARS, DECISIVE));
        assertThrows(IllegalArgumentException.class, () -> new DecisiveCrossing(flat(), 1, DECISIVE));
        assertThrows(IllegalArgumentException.class, () -> new DecisiveCrossing(flat(), SLOPE_BARS, -1));
        assertThrows(IllegalArgumentException.class,
            () -> new DecisiveCrossing(flat(), SLOPE_BARS, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> crossing().setPriceExtractor(null));
        assertThrows(IllegalArgumentException.class, () -> crossing().setSlopeSeries(null));
        assertThrows(IllegalArgumentException.class, () -> crossing().setVolatilityCalculator(null));
        assertThrows(IllegalArgumentException.class, () -> crossing().setSlopeAnchor(null));
        assertThrows(IllegalArgumentException.class, () -> DecisiveCrossing.naive(null));
    }

    private static DecisiveCrossing crossing() {
        return new DecisiveCrossing(flat(), SLOPE_BARS, DECISIVE)
            .setVolatilityCalculator(volatility(1));
    }

    /** Порог 1.5 и окно заданной длины - на нём окно поперёк вершины и нога от неё расходятся. */
    private static DecisiveCrossing steep(int slopeBars) {
        return new DecisiveCrossing(flat(), slopeBars, 1.5).setVolatilityCalculator(volatility(1));
    }

    /** Разворот - откат больше чем на единицу цены. */
    private static ZigZagExtremeLocator zigzag() {
        return new ZigZagExtremeLocator(volatility(1), 1);
    }

    /** Волатильность задаётся числом, чтобы порог читался прямо в единицах цены за бар. */
    private static VolatilityCalculator volatility(double value) {
        return candles -> value;
    }

    /** Кривая-прямая на уровне 100: пересечения считаются с ней, а не со средней от самой цены. */
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
