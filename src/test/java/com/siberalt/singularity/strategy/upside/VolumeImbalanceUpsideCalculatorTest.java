package com.siberalt.singularity.strategy.upside;

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

class VolumeImbalanceUpsideCalculatorTest {
    private static final Instant START = Instant.parse("2021-06-03T10:00:00Z");

    private final VolumeImbalanceUpsideCalculator calculator = new VolumeImbalanceUpsideCalculator(3);

    /**
     * History that leans one way half as hard as the reading does, so the reading comes out at
     * about tanh(2) - unusual, but not the most one-sided thing this instrument ever does.
     */
    @Test
    void readsHeavyBuyingAsStrongButNotAbsolute() {
        List<Candle> window = new ArrayList<>(historyLeaning(30, 60, 40));
        window.addAll(bars(split(100, 0), split(100, 0), split(100, 0)));

        double signal = calculator.calculate(window).signal();

        assertEquals(Math.tanh(1 / 0.2), signal, 1e-6);
        assertTrue(signal > 0.99);
    }

    @Test
    void readsHeavySellingAsTheMirrorImage() {
        List<Candle> window = new ArrayList<>(historyLeaning(30, 60, 40));
        window.addAll(bars(split(0, 100), split(0, 100), split(0, 100)));

        assertEquals(-Math.tanh(1 / 0.2), calculator.calculate(window).signal(), 1e-6);
    }

    /**
     * The point of normalising. A flow no more lopsided than usual is not a signal, and the raw
     * share it comes from - a fifth of the volume - would have been read as one on any threshold
     * set low enough to ever trigger.
     */
    @Test
    void readsAnOrdinaryImbalanceAsOrdinary() {
        List<Candle> window = new ArrayList<>(historyLeaning(30, 60, 40));
        window.addAll(bars(split(60, 40), split(60, 40), split(60, 40)));

        assertEquals(Math.tanh(1), calculator.calculate(window).signal(), 1e-6);
    }

    @Test
    void weighsBarsByWhatTheyTraded() {
        List<Candle> window = new ArrayList<>(historyLeaning(30, 60, 40));
        // A busy bar of buying against a quiet one of selling.
        window.addAll(bars(split(1000, 0), split(0, 10), split(0, 0)));

        assertEquals(Math.tanh(((1000.0 - 10) / 1010) / 0.2), calculator.calculate(window).signal(), 1e-6);
    }

    /**
     * The split only appears partway through the history. Reading its absence as balanced flow
     * would have the calculator assert equilibrium from missing data.
     */
    @Test
    void saysNothingWhenNoBarCarriesTheSplit() {
        List<Candle> window = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            window.add(candle(i, 0, 0));
        }

        assertEquals(Upside.NEUTRAL, calculator.calculate(window));
    }

    @Test
    void saysNothingWithNoHistoryToMeasureAgainst() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(bars(split(100, 0), split(100, 0), split(100, 0))));
    }

    @Test
    void reportsHowMuchOfTheReadingCarriedASplit() {
        List<Candle> window = new ArrayList<>(historyLeaning(30, 60, 40));
        window.addAll(bars(split(0, 0), split(80, 20), split(0, 0)));

        assertEquals(1.0 / 3, calculator.calculate(window).strength(), 1e-9);
    }

    @Test
    void refusesAnEmptyPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeImbalanceUpsideCalculator(0));
    }

    /** Bars all leaning the same way, giving a typical imbalance of (buy - sell) / (buy + sell). */
    private List<Candle> historyLeaning(int count, long buy, long sell) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            candles.add(candle(i, buy, sell));
        }

        return candles;
    }

    private List<Candle> bars(long[]... splits) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < splits.length; i++) {
            candles.add(candle(1000 + i, splits[i][0], splits[i][1]));
        }

        return candles;
    }

    private Candle candle(int index, long buy, long sell) {
        Quotation price = Quotation.of(100);

        return new Candle(
            "TEST",
            new TimePoint(START.plusSeconds(index * 60L)),
            price, price, price, price,
            buy + sell, buy, sell
        );
    }

    private long[] split(long buy, long sell) {
        return new long[]{buy, sell};
    }
}
