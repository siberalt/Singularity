package com.siberalt.singularity.strategy.upside.extreme;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Расхождение цены и MACD.
 * <p>
 * Ряды собираются линейными отрезками, потому что для линии MACD наклон отрезка и есть её значение: на
 * равномерном ходу с шагом {@code s} зазор быстрой и медленной средних выходит примерно на
 * {@code s * (slow - fast) / 2}. Поэтому «падение вдвое медленнее» - это и есть «импульс выше», и ряд можно
 * построить так, чтобы расхождение в нём было по построению, а не по совпадению.
 */
class MacdDivergenceUpsideCalculatorTest {
    private static final int VICINITY = 5;
    /** Свежести дан запас: иначе каждый тест заодно проверял бы границу, и видно это было бы плохо. */
    private static final int FRESHNESS = VICINITY + 3;

    /**
     * Цена падает ниже прошлого минимума, но падает вдвое медленнее - значит линия MACD выше, чем была.
     * Первый минимум 60 после хода −1.00 за бар, второй 59 после −0.53: ниже по цене, выше по импульсу.
     */
    @Test
    void findsTheBullishDivergence() {
        Upside upside = divergence().calculate(fallingTwiceTo(59));

        assertEquals(1, upside.signal());
        assertTrue(upside.strength() > 0);
    }

    /** Зеркало: новый максимум цены при более низком максимуме индикатора. */
    @Test
    void findsTheBearishDivergence() {
        Upside upside = divergence().calculate(
            ramps(100, leg(140, 40), leg(125, 20), leg(141, 30), leg(137, VICINITY + 1)));

        assertEquals(-1, upside.signal());
    }

    /**
     * Второй минимум ниже первого, и импульс тоже ниже - это подтверждение тренда, а не расхождение.
     * Отличить одно от другого и есть вся работа калькулятора.
     */
    @Test
    void saysNothingWhenMomentumAgreesWithPrice() {
        assertEquals(Upside.NEUTRAL, divergence().calculate(
            ramps(100, leg(90, 30), leg(95, 15), leg(70, 30), leg(74, VICINITY + 1))));
    }

    /**
     * На самом минимуме сигнала нет и быть не может: пивот опознаётся только через правую окрестность
     * баров после себя. Это и есть отсутствие заглядывания в будущее, проверенное на том же ряде.
     */
    @Test
    void cannotFireAtTheLowItself() {
        List<Candle> full = fallingTwiceTo(59);
        List<Candle> untilTheLow = full.subList(0, full.size() - VICINITY - 1);

        assertEquals(Upside.NEUTRAL, divergence().calculate(untilTheLow));
    }

    /** Расхождение, о котором уже нельзя торговать, - история, а не сигнал. */
    @Test
    void forgetsAStalePivot() {
        List<Candle> stale = ramps(100, leg(60, 40), leg(75, 20), leg(59, 30),
            leg(80, FRESHNESS + 20));

        assertEquals(Upside.NEUTRAL, divergence().calculate(stale));
    }

    /** Пока у линии нет периода, читать нечего. */
    @Test
    void saysNothingUntilTheIndicatorHasItsPeriod() {
        assertEquals(Upside.NEUTRAL, divergence().calculate(null));
        assertEquals(Upside.NEUTRAL, divergence().calculate(List.of()));
        assertEquals(Upside.NEUTRAL, divergence().calculate(ramps(100, leg(90, 10))));
    }

    /** Сила нормирована на цену, иначе бумаги за 100 и за 10000 рублей несравнимы. */
    @Test
    void measuresStrengthAgainstThePrice() {
        Upside cheap = divergence().calculate(fallingTwiceTo(59));
        Upside dear = divergence().calculate(
            ramps(1000, leg(600, 40), leg(750, 20), leg(590, 30), leg(630, VICINITY + 1)));

        assertEquals(cheap.strength(), dear.strength(), 1e-9);
    }

    /**
     * Свежесть строже правой окрестности означает, что сигнала не будет никогда: пивот моложе своей
     * окрестности не бывает. Фабрика это знает и отказывается.
     */
    @Test
    void refusesFreshnessThePivotCannotReach() {
        assertThrows(IllegalArgumentException.class,
            () -> MacdDivergenceUpsideCalculator.builder().pivots(5).freshness(4).build());
    }

    /** С локаторами снаружи окрестность билдеру неизвестна, и строгую свежесть он уже не оспаривает. */
    @Test
    void takesTheCallersWordOnLocatorsItDidNotBuild() {
        assertEquals(Upside.NEUTRAL, MacdDivergenceUpsideCalculator.builder()
            .locators(PivotPointExtremeLocator.ofMinimums(5), PivotPointExtremeLocator.ofMaximums(5))
            .freshness(1)
            .build()
            .calculate(fallingTwiceTo(59)));
    }

    @Test
    void refusesWhatCannotBeComputed() {
        assertThrows(IllegalArgumentException.class,
            () -> MacdDivergenceUpsideCalculator.builder().source(null).build());
        assertThrows(IllegalArgumentException.class,
            () -> MacdDivergenceUpsideCalculator.builder().locators(null, null).build());
        assertThrows(IllegalArgumentException.class,
            () -> MacdDivergenceUpsideCalculator.builder()
                .locators(PivotPointExtremeLocator.ofMinimums(VICINITY),
                    PivotPointExtremeLocator.ofMaximums(VICINITY))
                .freshness(0)
                .build());
        assertThrows(IllegalArgumentException.class,
            () -> MacdDivergenceUpsideCalculator.builder().macd(26, 12, 9).build());
        assertThrows(IllegalArgumentException.class,
            () -> MacdDivergenceUpsideCalculator.builder().pivots(0).build());
    }

    /**
     * Свечи без индекса отвергаются вслух. Молча не находить расхождений никогда - худший из отказов: он
     * выглядит как «сигнала нет», а не как «считать нечем».
     */
    @Test
    void refusesCandlesWithoutAnIndex() {
        List<Candle> unindexed = new ArrayList<>();

        for (Candle candle : fallingTwiceTo(59)) {
            unindexed.add(Candle.of(TimePoint.NULL, candle.getCloseAsDouble()));
        }

        assertThrows(IllegalArgumentException.class, () -> divergence().calculate(unindexed));
    }

    /**
     * Свежесть, равная окрестности, оставляет сигналу ровно один бар: раньше пивот не подтверждён, позже
     * уже стар. Это не дефект, а то, что значит требовать самого свежего возможного разворота.
     */
    @Test
    void livesForOneBarWhenFreshnessIsTheVicinityItself() {
        MacdDivergenceUpsideCalculator strict = MacdDivergenceUpsideCalculator.builder()
            .pivots(VICINITY)
            .freshness(VICINITY)
            .build();

        assertEquals(1, strict.calculate(
            ramps(100, leg(60, 40), leg(75, 20), leg(59, 30), leg(63, VICINITY))).signal());
        assertEquals(Upside.NEUTRAL, strict.calculate(
            ramps(100, leg(60, 40), leg(75, 20), leg(59, 30), leg(63, VICINITY + 1))));
    }

    private static MacdDivergenceUpsideCalculator divergence() {
        return MacdDivergenceUpsideCalculator.builder()
            .pivots(VICINITY)
            .freshness(FRESHNESS)
            .build();
    }

    /** Крутое падение до 60, отскок до 75, вдвое более медленное падение ниже, и разворот. */
    private static List<Candle> fallingTwiceTo(double second) {
        return ramps(100, leg(60, 40), leg(75, 20), leg(second, 30), leg(63, VICINITY + 1));
    }

    private static double[] leg(double target, int bars) {
        return new double[]{target, bars};
    }

    private static List<Candle> ramps(double start, double[]... legs) {
        List<Candle> candles = new ArrayList<>();
        double price = start;

        candles.add(candleOf(candles.size(), price));

        for (double[] leg : legs) {
            double target = leg[0];
            int bars = (int) leg[1];

            for (int step = 1; step <= bars; step++) {
                candles.add(candleOf(candles.size(), price + (target - price) * step / bars));
            }

            price = target;
        }

        return candles;
    }

    private static Candle candleOf(int at, double price) {
        Quotation value = Quotation.of(price);

        // Индексы обязаны расти: по ним локатор считает шаг бара и склеивает близкие пивоты.
        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(86400L * at)),
            value, value, value, value, 1);
    }
}
