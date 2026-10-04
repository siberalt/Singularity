package com.siberalt.singularity.strategy.upside.trend;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Пересечение средних как состояние: сторона в сигнале, величина разрыва в силе. */
class MovingAverageCrossUpsideCalculatorTest {
    @Test
    void saysNothingUntilTheSlowAverageHasItsPeriod() {
        MovingAverageCrossUpsideCalculator cross = MovingAverageCrossUpsideCalculator.ofSma(2, 5);

        assertEquals(Upside.NEUTRAL, cross.calculate(rising(4)));
        assertEquals(Upside.NEUTRAL, cross.calculate(null));
        assertEquals(Upside.NEUTRAL, cross.calculate(List.of()));
    }

    @Test
    void saysUpWhenTheFastAverageIsAbove() {
        assertEquals(1, MovingAverageCrossUpsideCalculator.ofSma(2, 5)
            .calculate(rising(20)).signal());
    }

    @Test
    void saysDownWhenTheFastAverageIsBelow() {
        assertEquals(-1, MovingAverageCrossUpsideCalculator.ofSma(2, 5)
            .calculate(falling(20)).signal());
    }

    /**
     * Главное отличие от {@link EmaSpreadTrendUpsideCalculator}: сигнал - ступенька, а не сила. Свежий
     * крест с разрывом в сотые доли процента даёт ту же единицу, что и давний, - иначе стратегия с порогом
     * покупки не вошла бы в позицию на самом пересечении, то есть ровно тогда, когда правило велит.
     */
    @Test
    void saysTheSameWhateverTheGapIs() {
        Upside wide = MovingAverageCrossUpsideCalculator.ofSma(2, 5).calculate(rising(20));
        Upside narrow = MovingAverageCrossUpsideCalculator.ofSma(2, 5).calculate(barelyRising(20));

        assertEquals(wide.signal(), narrow.signal());
        assertTrue(wide.strength() > narrow.strength(),
            "сила должна различать разрывы: " + wide.strength() + " против " + narrow.strength());
    }

    @Test
    void keepsQuietInsideTheDeadZone() {
        MovingAverageCrossUpsideCalculator wide = new MovingAverageCrossUpsideCalculator(
            MovingAverageCrossUpsideCalculator.Average.SMA, 2, 5, 0.5);

        assertEquals(Upside.NEUTRAL, wide.calculate(barelyRising(20)));
    }

    /** Та же механика на экспоненциальных средних - в этом и смысл делегирования. */
    @Test
    void takesAnyAverageItIsGiven() {
        assertEquals(1, MovingAverageCrossUpsideCalculator.ofEma(2, 5).calculate(rising(20)).signal());
        assertEquals(-1, MovingAverageCrossUpsideCalculator.ofEma(2, 5).calculate(falling(20)).signal());
    }

    @Test
    void isTheGoldenCrossByDefault() {
        MovingAverageCrossUpsideCalculator cross = MovingAverageCrossUpsideCalculator.goldenCross();

        assertEquals(Upside.NEUTRAL, cross.calculate(rising(199)));
        assertEquals(1, cross.calculate(rising(300)).signal());
    }

    @Test
    void refusesPeriodsThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class,
            () -> MovingAverageCrossUpsideCalculator.ofSma(5, 5));
        assertThrows(IllegalArgumentException.class,
            () -> MovingAverageCrossUpsideCalculator.ofSma(0, 5));
        assertThrows(IllegalArgumentException.class,
            () -> MovingAverageCrossUpsideCalculator.ofSma(10, 5));
        assertThrows(IllegalArgumentException.class, () -> new MovingAverageCrossUpsideCalculator(
            MovingAverageCrossUpsideCalculator.Average.SMA, 2, 5, -0.1));
        assertThrows(IllegalArgumentException.class,
            () -> new MovingAverageCrossUpsideCalculator(null, 2, 5));
    }

    private static List<Candle> rising(int count) {
        return seriesOf(count, at -> 100 + 2.0 * at);
    }

    private static List<Candle> barelyRising(int count) {
        return seriesOf(count, at -> 100 + 0.001 * at);
    }

    private static List<Candle> falling(int count) {
        return seriesOf(count, at -> 100 - 2.0 * at);
    }

    private static List<Candle> seriesOf(int count, java.util.function.IntToDoubleFunction price) {
        List<Candle> candles = new ArrayList<>(count);

        for (int at = 0; at < count; at++) {
            Quotation value = Quotation.of(price.applyAsDouble(at));

            candles.add(new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
                value, value, value, value, 1));
        }

        return candles;
    }
}
