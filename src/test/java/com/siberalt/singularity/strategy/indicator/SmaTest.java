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

class SmaTest {
    private List<Candle> closes(double... closes) {
        List<Candle> candles = new ArrayList<>(closes.length);

        for (double close : closes) {
            candles.add(Candle.of(TimePoint.NULL, 100, close, close, close, close));
        }

        return candles;
    }

    @Test
    void should_SayNothing_UntilItHasAFullWindow() {
        var sma = new Sma(3);

        assertTrue(Double.isNaN(sma.add(1)));
        assertTrue(Double.isNaN(sma.add(2)));
        assertFalse(sma.ready());
        assertEquals(2, sma.add(3), 1e-9);
        assertTrue(sma.ready());
    }

    @Test
    void should_DropThePriceThatLeftTheWindow() {
        double[] series = Sma.seriesOf(closes(1, 2, 3, 4, 10), 3, Candle::getCloseAsDouble);

        assertEquals(2, series[2], 1e-9);
        assertEquals(3, series[3], 1e-9);
        assertEquals(17.0 / 3, series[4], 1e-9);
    }

    @Test
    void should_AgreeWithTheOneShotForm_WhenFedPriceByPrice() {
        List<Candle> candles = closes(5, 7, 6, 9, 11, 10);
        var sma = new Sma(4);

        candles.forEach(sma::add);

        assertEquals(Sma.of(candles, 4), sma.value(), 1e-9);
        assertEquals((6 + 9 + 11 + 10) / 4.0, sma.value(), 1e-9);
    }

    @Test
    void should_UseTheGivenPrice() {
        List<Candle> candles = List.of(
            Candle.of(TimePoint.NULL, 100, 0, 10, 1, 5),
            Candle.of(TimePoint.NULL, 100, 0, 20, 1, 5)
        );

        assertEquals(15, Sma.of(candles, 2, Candle::getHighAsDouble), 1e-9);
    }

    @Test
    void should_SayNothing_WhenThereAreTooFewCandlesOrNone() {
        assertTrue(Double.isNaN(Sma.of(null, 3)));
        assertTrue(Double.isNaN(Sma.of(closes(1, 2), 3)));
    }

    @Test
    void should_Forget_WhenReset() {
        var sma = new Sma(2);
        sma.add(10);
        sma.add(20);

        sma.reset();

        assertFalse(sma.ready());
        assertTrue(Double.isNaN(sma.add(4)));
        assertEquals(5, sma.add(6), 1e-9);
    }

    @Test
    void should_RefuseAPeriodThatIsNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> new Sma(0));
    }
}
