package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProximityGroupingExtremeLocatorTest {
    private static final Comparator<Candle> MINIMUMS = Comparator.comparingDouble(Candle::getCloseAsDouble);
    private static final Comparator<Candle> MAXIMUMS = MINIMUMS.reversed();

    /** The deepest of a run survives - the one the run is about - not the shallowest. */
    @Test
    void keepsTheLowestOfMinimumsCloseTogether() {
        List<Candle> candles = candles(1, 5, 3, 5, 1, 5);

        assertEquals(
            List.of(candles.get(3)),
            grouping(candles, MINIMUMS, 10, 1, 3).locate(candles)
        );
    }

    @Test
    void keepsTheHighestOfMaximumsCloseTogether() {
        List<Candle> candles = candles(1, 5, 7, 5, 9, 5);

        assertEquals(
            List.of(candles.get(3)),
            grouping(candles, MAXIMUMS, 10, 1, 3).locate(candles)
        );
    }

    @Test
    void keepsExtremesFartherApartThanTheReachApart() {
        List<Candle> candles = IntStream.range(0, 30).mapToObj(i -> candle(i, 5)).toList();

        assertEquals(
            List.of(candles.get(1), candles.get(20)),
            grouping(candles, MINIMUMS, 10, 1, 20).locate(candles)
        );
    }

    /** Each extreme within reach of the one before it joins the run, however long the run gets. */
    @Test
    void chainsARunThroughItsNeighbours() {
        List<Candle> candles = IntStream.range(0, 30).mapToObj(i -> candle(i, i == 16 ? 1 : 5)).toList();

        assertEquals(
            List.of(candles.get(16)),
            grouping(candles, MINIMUMS, 10, 0, 8, 16).locate(candles)
        );
    }

    /**
     * Rolled-up bars keep the index of the raw bar they opened on, so neighbours sit sixty units
     * apart. Two extremes two bars from each other are close together however far apart their
     * indexes are.
     */
    @Test
    void countsTheReachInBarsNotInIndexUnits() {
        List<Candle> candles = candles(60, 5, 3, 5, 1, 5);

        assertEquals(
            List.of(candles.get(3)),
            grouping(candles, MINIMUMS, 10, 1, 3).locate(candles)
        );
    }

    @Test
    void keepsTheFirstOfEquals() {
        List<Candle> candles = candles(1, 5, 1, 5, 1, 5);

        assertEquals(
            List.of(candles.get(1)),
            grouping(candles, MINIMUMS, 10, 1, 3).locate(candles)
        );
    }

    @Test
    void passesExtremesThroughWithNoReach() {
        List<Candle> candles = candles(1, 5, 3, 5, 1, 5);

        assertEquals(
            List.of(candles.get(1), candles.get(3)),
            grouping(candles, MINIMUMS, 0, 1, 3).locate(candles)
        );
    }

    @Test
    void findsNothingWhereTheWrappedLocatorFindsNothing() {
        List<Candle> candles = candles(1, 5, 3, 5);

        assertEquals(List.of(), grouping(candles, MINIMUMS, 10).locate(candles));
    }

    @Test
    void refusesToBeBuiltWithoutWhatItNeeds() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProximityGroupingExtremeLocator(null, MINIMUMS, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new ProximityGroupingExtremeLocator(candles -> List.of(), null, 10));
        assertThrows(IllegalArgumentException.class,
            () -> new ProximityGroupingExtremeLocator(candles -> List.of(), MINIMUMS, -1));
    }

    /** Groups whatever sits at these positions of the candles, as if a locator had found it there. */
    private ProximityGroupingExtremeLocator grouping(List<Candle> candles, Comparator<Candle> comparator, int area, int... at) {
        List<Candle> found = IntStream.of(at).mapToObj(candles::get).toList();

        return new ProximityGroupingExtremeLocator(ignored -> found, comparator, area);
    }

    /** Candles closing at these prices, their indexes {@code step} apart. */
    private List<Candle> candles(long step, double... closes) {
        return IntStream.range(0, closes.length)
            .mapToObj(i -> candle(i * step, closes[i]))
            .toList();
    }

    private Candle candle(long index, double close) {
        Quotation price = Quotation.of(close);

        return new Candle("instrument1", new TimePoint(index), price, price, price, price, 0);
    }
}
