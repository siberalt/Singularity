package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceChangeUpsideCalculatorTest {
    private static final Upside RISE = new Upside(1, 1);

    private static final Upside FALL = new Upside(-1, 1);

    private Candle candle(double close) {
        return Candle.of(TimePoint.NULL, 100, close, close, close, close);
    }

    private Candle candle(double high, double close) {
        return Candle.of(TimePoint.NULL, 100, close, high, close, close);
    }

    private List<Candle> closes(double... closes) {
        List<Candle> candles = new ArrayList<>();

        for (double close : closes) {
            candles.add(candle(close));
        }

        return candles;
    }

    @Test
    void should_ReturnRise_WhenPriceRoseAboveThreshold() {
        var calculator = new PriceChangeUpsideCalculator(2, 5);

        assertEquals(RISE, calculator.calculate(closes(100, 102, 106)));
    }

    @Test
    void should_ReturnFall_WhenPriceFellBelowThreshold() {
        var calculator = new PriceChangeUpsideCalculator(2, 5);

        assertEquals(FALL, calculator.calculate(closes(100, 98, 94)));
    }

    @Test
    void should_ReturnNeutral_WhenMoveIsInsideThresholds() {
        var calculator = new PriceChangeUpsideCalculator(2, 5);

        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 102, 104)));
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 98, 96)));
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 100, 100)));
    }

    @Test
    void should_ReturnRise_WhenMoveEqualsRiseThreshold() {
        var calculator = new PriceChangeUpsideCalculator(1, 50);

        // 150 / 100 is exact in binary, so the move is exactly on the threshold.
        assertEquals(RISE, calculator.calculate(closes(100, 150)));
    }

    @Test
    void should_ReturnFall_WhenMoveEqualsFallThreshold() {
        var calculator = new PriceChangeUpsideCalculator(1, 50);

        assertEquals(FALL, calculator.calculate(closes(100, 50)));
    }

    @Test
    void should_UseSeparateThresholds_ForEachSide() {
        var calculator = new PriceChangeUpsideCalculator(1, 7, 5);

        // Six per cent up is not enough, six per cent down is.
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 106)));
        assertEquals(FALL, calculator.calculate(closes(100, 94)));
        assertEquals(RISE, calculator.calculate(closes(100, 107)));
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 96)));
    }

    @Test
    void should_MeasureOnlyOverPeriod_IgnoringOlderCandles() {
        var calculator = new PriceChangeUpsideCalculator(2, 5);

        // The 50 -> 100 jump lies outside the window: over the last two candles the price is flat.
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(50, 100, 100, 100)));
    }

    @Test
    void should_CompareLastCandleWithOneExactlyPeriodBack() {
        var calculator = new PriceChangeUpsideCalculator(3, 5);

        // Candles in between do not matter, only the first and last of the window.
        assertEquals(RISE, calculator.calculate(closes(100, 90, 120, 106)));
        assertEquals(FALL, calculator.calculate(closes(100, 120, 80, 94)));
    }

    @Test
    void should_ReturnNeutral_WhenNotEnoughCandles() {
        var calculator = new PriceChangeUpsideCalculator(3, 5);

        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 200, 300)));
    }

    @Test
    void should_Work_WhenExactlyPeriodPlusOneCandles() {
        var calculator = new PriceChangeUpsideCalculator(3, 5);

        assertEquals(RISE, calculator.calculate(closes(100, 100, 100, 110)));
    }

    @Test
    void should_ReturnNeutral_WhenEmptyList() {
        assertEquals(Upside.NEUTRAL, new PriceChangeUpsideCalculator(1, 5).calculate(List.of()));
    }

    @Test
    void should_ReturnNeutral_WhenNullList() {
        assertEquals(Upside.NEUTRAL, new PriceChangeUpsideCalculator(1, 5).calculate(null));
    }

    @Test
    void should_ReturnNeutral_WhenPriceIsNotPositive() {
        var calculator = new PriceChangeUpsideCalculator(1, 5);

        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(0, 100)));
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(100, 0)));
        assertEquals(Upside.NEUTRAL, calculator.calculate(closes(-100, 100)));
    }

    @Test
    void should_ReadMoveOffCustomPriceExtractor() {
        var byHigh = new PriceChangeUpsideCalculator(1, 5).setPriceExtractor(Candle::high);
        var candles = List.of(candle(100, 100), candle(110, 100));

        // The close has not moved, the high has gone up ten per cent.
        assertEquals(Upside.NEUTRAL, new PriceChangeUpsideCalculator(1, 5).calculate(candles));
        assertEquals(RISE, byHigh.calculate(candles));
    }

    @Test
    void should_ReturnItself_FromSetPriceExtractor() {
        var calculator = new PriceChangeUpsideCalculator(1, 5);

        assertEquals(calculator, calculator.setPriceExtractor(Candle::close));
    }

    @Test
    void should_Throw_WhenPeriodIsLessThanOne() {
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(-1, 5, 5));
    }

    @Test
    void should_Throw_WhenThresholdIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(1, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(1, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(1, -5, 5));
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(1, 5, -5));
        assertThrows(IllegalArgumentException.class, () -> new PriceChangeUpsideCalculator(1, 0));
    }
}
