package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarSpacingTest {
    private static final Instant START = Instant.parse("2023-01-02T07:00:00Z");

    @Test
    void rawCandlesAreOneIndexApart() {
        assertEquals(1, BarSpacing.of(candles(0, 1, 2, 3, 4)), 1e-9);
        assertEquals(1, BarSpacing.stepOf(candles(0, 1, 2, 3, 4)));
    }

    @Test
    void hourlyBarsAreSixtyApartBecauseTheyCarryTheIndexOfTheirMinute() {
        assertEquals(60, BarSpacing.of(candles(0, 60, 120, 180)), 1e-9);
        assertEquals(60, BarSpacing.stepOf(candles(0, 60, 120, 180)));
    }

    @Test
    void unevenSpacingIsAveraged() {
        // An hour at the edge of a session holds fewer minutes: 50, 60, 45, 60 - a span of 215 over four.
        List<Candle> uneven = candles(0, 50, 110, 155, 215);

        assertEquals(53.75, BarSpacing.of(uneven), 1e-9);
        assertEquals(53, BarSpacing.stepOf(uneven));
    }

    @Test
    void aWindowTooShortToMeasureSpansOneIndexPerBar() {
        assertEquals(1, BarSpacing.of(candles(7)), 1e-9);
        assertEquals(1, BarSpacing.of(List.of()), 1e-9);
        assertEquals(1, BarSpacing.of(null), 1e-9);
        assertEquals(1, BarSpacing.stepOf(candles(7)));
    }

    @Test
    void barsAreTurnedIntoIndexUnits() {
        assertEquals(30, BarSpacing.unitsOf(candles(0, 1, 2, 3), 30));
        assertEquals(1800, BarSpacing.unitsOf(candles(0, 60, 120, 180), 30));
    }

    private static List<Candle> candles(long... indices) {
        List<Candle> candles = new ArrayList<>();

        for (long index : indices) {
            Quotation price = Quotation.of(100);
            candles.add(new Candle(1, new TimePoint(index, START.plusSeconds(60L * index)),
                price, price, price, price, 1));
        }

        return candles;
    }
}
