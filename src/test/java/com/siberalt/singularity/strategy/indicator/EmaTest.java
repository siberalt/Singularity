package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmaTest {
    private List<Candle> closes(double... closes) {
        List<Candle> candles = new ArrayList<>(closes.length);

        for (double close : closes) {
            candles.add(Candle.of(TimePoint.NULL, 100, close, close, close, close));
        }

        return candles;
    }

    @Test
    void should_SayNothing_UntilItHasAPeriodOfPrices() {
        var ema = new Ema(3);

        assertTrue(Double.isNaN(ema.add(1)));
        assertTrue(Double.isNaN(ema.add(2)));
        assertFalse(ema.ready());
        assertEquals(2, ema.add(3), 1e-9);
        assertTrue(ema.ready());
    }

    /** Seeded with the mean of the first period, then each price pulled in with 2 / (period + 1). */
    @Test
    void should_SmoothEachPriceIn_AfterTheSeed() {
        double[] series = Ema.seriesOf(closes(1, 2, 3, 4, 10), 3, Candle::getCloseAsDouble);

        // alpha = 0.5: seed 2, then 2 + 0.5 * (4 - 2) = 3, then 3 + 0.5 * (10 - 3) = 6.5
        assertEquals(2, series[2], 1e-9);
        assertEquals(3, series[3], 1e-9);
        assertEquals(6.5, series[4], 1e-9);
    }

    @Test
    void should_StayPut_WhenThePriceDoesNotMove() {
        assertEquals(100, Ema.of(closes(100, 100, 100, 100, 100, 100), 4), 1e-9);
    }

    @Test
    void should_AgreeWithTheOneShotForm_WhenFedPriceByPrice() {
        List<Candle> candles = closes(5, 7, 6, 9, 11, 10);
        var ema = new Ema(3);

        candles.forEach(ema::add);

        assertEquals(Ema.of(candles, 3), ema.value(), 1e-9);
    }

    @Test
    void should_FollowAFallFasterThanASmaOfTheSameWidth() {
        List<Candle> candles = closes(100, 100, 100, 100, 100, 50);

        assertTrue(Ema.of(candles, 5) < Sma.of(candles, 5));
    }

    @Test
    void should_SayNothing_WhenThereAreTooFewCandlesOrNone() {
        assertTrue(Double.isNaN(Ema.of(null, 3)));
        assertTrue(Double.isNaN(Ema.of(closes(1, 2), 3)));
    }

    @Test
    void should_Forget_WhenReset() {
        var ema = new Ema(2);
        ema.add(10);
        ema.add(20);

        ema.reset();

        assertFalse(ema.ready());
        assertTrue(Double.isNaN(ema.value()));
        ema.add(4);
        assertEquals(5, ema.add(6), 1e-9);
    }

    @Test
    void should_RefuseAPeriodThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new Ema(0));
    }
}
