package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Угол хода по приращениям и такой же угол по объёму. */
class ChangeAngleSignalSourceTest {
    private static final int BARS = 10;

    /** Все бары в одну сторону - предельные 45°, то есть сигнал ровно единица. */
    @Test
    void readsAOneSidedRunAsItsLimit() {
        assertEquals(1, angle().calculate(prices(100, 102, 104, 106, 108, 110, 112, 114, 116, 118, 120))
            .confidence(), 1e-9);
        assertEquals(-1, angle().calculate(prices(120, 118, 116, 114, 112, 110, 108, 106, 104, 102, 100))
            .confidence(), 1e-9);
        assertEquals(45, angle().anglesOf(
            prices(100, 102, 104, 106, 108, 110, 112, 114, 116, 118, 120)).price(), 1e-9);
    }

    /** Ход туда и обратно, сошедшийся в ничью, - ноль, сколько бы его ни было. */
    @Test
    void readsAWashAsNothing() {
        assertEquals(0, angle().calculate(prices(100, 110, 100, 110, 100, 110, 100, 110, 100, 110, 100))
            .confidence(), 1e-9);
    }

    /**
     * Та же мера на том же ходу, умноженном на сто: угол не зависит от цены бумаги. Ровно за этим вертикаль
     * и нормируется - иначе бумага за 100 и за 10000 несравнимы.
     */
    @Test
    void measuresTheSameAngleWhateverThePrice() {
        double cheap = angle().anglesOf(prices(100, 104, 102, 108, 106, 112, 110, 116, 114, 120, 118))
            .price();
        double dear = angle().anglesOf(
            prices(10000, 10400, 10200, 10800, 10600, 11200, 11000, 11600, 11400, 12000, 11800)).price();

        assertEquals(cheap, dear, 1e-9);
        assertTrue(cheap > 0 && cheap < 45, "рваный ход - между нулём и пределом, " + cheap);
    }

    /** Предел не обходится ничем: какой бы ход ни был, он лежит в ±45°. */
    @Test
    void neverLeavesTheForty_fiveDegrees() {
        List<double[]> runs = List.of(
            new double[]{100, 101, 99, 140, 60, 200, 10, 300, 1, 500, 2},
            new double[]{5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 400},
            new double[]{400, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}
        );

        for (double[] run : runs) {
            double degrees = angle().anglesOf(prices(run)).price();

            assertTrue(Math.abs(degrees) <= 45 + 1e-9, "угол вне предела: " + degrees);
        }
    }

    /** Объём читается той же мерой: растёт на каждом баре - сила единица, падает - ноль, стоит - половина. */
    @Test
    void readsTheVolumeByTheSameMeasure() {
        long[] rising = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110};
        long[] falling = {110, 100, 90, 80, 70, 60, 50, 40, 30, 20, 10};
        long[] flat = {50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50};

        assertEquals(1, angle().calculate(withVolume(rising)).strength(), 1e-9);
        assertEquals(0, angle().calculate(withVolume(falling)).strength(), 1e-9);
        assertEquals(0.5, angle().calculate(withVolume(flat)).strength(), 1e-9);
    }

    /**
     * Нулевой сигнал при высокой силе - не молчание, а сведение: объём набирается, цена никуда не идёт.
     * Поэтому пара чисел возвращается всегда, когда истории хватило.
     */
    @Test
    void tellsVolumeBuildingApartFromSilence() {
        List<Candle> candles = new ArrayList<>();
        long[] volumes = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110};

        for (int at = 0; at <= BARS; at++) {
            candles.add(candleOf(at, 100, volumes[at]));
        }

        Signal signal = angle().calculate(candles);

        assertEquals(0, signal.confidence(), 1e-9);
        assertEquals(1, signal.strength(), 1e-9);
    }

    /** Молчание означает ровно одно: истории не хватило. */
    @Test
    void saysNothingUntilTheWindowIsFull() {
        assertEquals(Signal.NEUTRAL, angle().calculate(null));
        assertEquals(Signal.NEUTRAL, angle().calculate(List.of()));
        assertEquals(Signal.NEUTRAL, angle().calculate(prices(100, 101, 102)));
        assertNull(angle().anglesOf(prices(100, 101, 102)));
    }

    /** Берутся последние bars приращений, а не всё, что дали. */
    @Test
    void looksAtTheLastBarsOnly() {
        // Первые десять баров идут вниз, последние десять - вверх строго.
        double[] run = {200, 190, 180, 170, 160, 150, 140, 130, 120, 110, 100,
            102, 104, 106, 108, 110, 112, 114, 116, 118, 120};

        assertEquals(1, angle().calculate(prices(run)).confidence(), 1e-9);
    }

    @Test
    void refusesAWindowWithNothingToMeasure() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeAngleSignalSource(0));
        assertThrows(IllegalArgumentException.class, () -> new ChangeAngleSignalSource(-1));
    }

    private static ChangeAngleSignalSource angle() {
        return new ChangeAngleSignalSource(BARS);
    }

    private static List<Candle> prices(double... closes) {
        List<Candle> candles = new ArrayList<>(closes.length);

        for (int at = 0; at < closes.length; at++) {
            candles.add(candleOf(at, closes[at], 1));
        }

        return candles;
    }

    private static List<Candle> withVolume(long... volumes) {
        List<Candle> candles = new ArrayList<>(volumes.length);

        for (int at = 0; at < volumes.length; at++) {
            candles.add(candleOf(at, 100 + at, volumes[at]));
        }

        return candles;
    }

    private static Candle candleOf(int at, double price, long volume) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, volume);
    }
}
