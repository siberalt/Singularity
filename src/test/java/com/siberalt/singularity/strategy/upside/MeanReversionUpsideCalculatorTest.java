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

class MeanReversionUpsideCalculatorTest {
    private static final Instant START = Instant.parse("2021-06-03T10:00:00Z");

    private final MeanReversionUpsideCalculator calculator = new MeanReversionUpsideCalculator(5);

    @Test
    void buysAPriceThatHasFallenBelowWhereItHasBeenSitting() {
        Upside upside = calculator.calculate(bars(100, 100, 100, 100, 90));

        assertTrue(upside.signal() > 0, "below the mean should read as something to buy");
    }

    @Test
    void sellsAPriceThatHasRisenAboveIt() {
        Upside upside = calculator.calculate(bars(100, 100, 100, 100, 110));

        assertTrue(upside.signal() < 0, "above the mean should read as something to sell");
    }

    /** The distance is in units of the window's own spread, so the same stray reads the same. */
    @Test
    void measuresTheStrayInTheSpreadOfWhatItStrayedFrom() {
        double[] calm = {100, 101, 99, 100, 96};
        double[] wild = {100, 110, 90, 100, 60};

        Upside fromCalm = calculator.calculate(bars(calm));
        Upside fromWild = calculator.calculate(bars(wild));

        // Four roubles below a quiet mean is a bigger stray than forty below a violent one.
        assertTrue(fromCalm.signal() > fromWild.signal(), fromCalm.signal() + " vs " + fromWild.signal());
    }

    @Test
    void saysNothingWhenThePriceHasNotMoved() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(bars(100, 100, 100, 100, 100)));
    }

    @Test
    void saysNothingBeforeTheWindowIsFull() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(bars(100, 90)));
    }

    /**
     * The confidence is the property the bet rests on: a price that keeps coming back has crossed
     * its average often, and one that walked off in a straight line has crossed once.
     */
    @Test
    void trustsAPriceThatKeepsComingBackMoreThanOneThatWalkedAway() {
        Upside crossing = calculator.calculate(bars(96, 104, 96, 104, 96));
        Upside walking = calculator.calculate(bars(90, 95, 100, 105, 110));

        assertEquals(1.0, crossing.strength(), 1e-9);
        assertTrue(walking.strength() <= 0.25, "a straight line barely crosses: " + walking.strength());
    }

    /**
     * The gate the inverted slope carries and this one lacked: only a stray that arrived in a
     * straight line is a move that may have run its course.
     */
    @Test
    void saysNothingAboutAStrayThatDidNotArriveInAStraightLine() {
        calculator.setMinStraightness(0.8);

        // Wanders up and down and happens to end low: a stray, but not a spent move.
        assertEquals(Upside.NEUTRAL, calculator.calculate(bars(100, 108, 96, 106, 92)));
        // Walks down steadily, ending below its own average.
        assertTrue(calculator.calculate(bars(108, 104, 100, 96, 92)).signal() > 0);
    }

    @Test
    void countsEveryStrayWithNoGate() {
        assertTrue(calculator.calculate(bars(100, 108, 96, 106, 92)).signal() > 0);
    }

    @Test
    void refusesAStraightnessOutsideItsRange() {
        assertThrows(IllegalArgumentException.class, () -> calculator.setMinStraightness(-0.1));
        assertThrows(IllegalArgumentException.class, () -> calculator.setMinStraightness(1.5));
    }

    @Test
    void refusesAPeriodTooShortToHaveASpread() {
        assertThrows(IllegalArgumentException.class, () -> new MeanReversionUpsideCalculator(1));
    }

    @Test
    void refusesNoPriceToRead() {
        assertThrows(IllegalArgumentException.class, () -> calculator.setPriceExtractor(null));
    }

    @Test
    void readsThePriceItIsToldTo() {
        // Steady by its typical price, but closing lower and lower - two different stories about
        // the same five bars, and the calculator has to tell whichever it was asked for.
        List<Candle> candles = new ArrayList<>();

        for (int index = 0; index < 5; index++) {
            candles.add(new Candle(
                "TEST",
                new TimePoint(START.plusSeconds(index * 60L)),
                Quotation.of(100),
                Quotation.of(100 - index),
                Quotation.of(101 + index),
                Quotation.of(99),
                100
            ));
        }

        double fromTypical = calculator.calculate(candles).signal();
        double fromClose = calculator.setPriceExtractor(Candle::close).calculate(candles).signal();

        assertTrue(fromClose > fromTypical, fromClose + " vs " + fromTypical);
    }

    private List<Candle> bars(double... prices) {
        List<Candle> candles = new ArrayList<>();

        for (int index = 0; index < prices.length; index++) {
            double price = prices[index];
            // High and low a rouble either side, close a rouble under: the typical price is the
            // plain one, the close is not.
            candles.add(new Candle(
                "TEST",
                new TimePoint(START.plusSeconds(index * 60L)),
                Quotation.of(price),
                Quotation.of(price - 1),
                Quotation.of(price + 1),
                Quotation.of(price + 2),
                100
            ));
        }

        return candles;
    }
}
