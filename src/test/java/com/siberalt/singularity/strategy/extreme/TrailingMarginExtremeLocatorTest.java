package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrailingMarginExtremeLocatorTest {
    private final List<Candle> candles = IntStream.range(0, 20).mapToObj(this::candle).toList();

    @Test
    void dropsWhatFallsWithinTheMarginOfTheEnd() {
        assertEquals(
            List.of(candles.get(3), candles.get(14)),
            filtering(5, 3, 14, 16, 19).locate(candles)
        );
    }

    /** The last candle a margin of five leaves standing is the fifth from the end. */
    @Test
    void keepsTheLastCandleTheMarginLeaves() {
        assertEquals(List.of(candles.get(14)), filtering(5, 14, 15).locate(candles));
    }

    @Test
    void passesEverythingThroughWithNoMargin() {
        assertEquals(
            List.of(candles.get(3), candles.get(19)),
            filtering(0, 3, 19).locate(candles)
        );
    }

    @Test
    void findsNothingInAWindowShorterThanTheMargin() {
        assertEquals(List.of(), filtering(5, 0, 1).locate(candles.subList(0, 4)));
    }

    @Test
    void findsNothingWhereTheWrappedLocatorFindsNothing() {
        assertEquals(List.of(), filtering(5).locate(candles));
    }

    @Test
    void refusesToBeBuiltWithoutWhatItNeeds() {
        assertThrows(IllegalArgumentException.class,
            () -> new TrailingMarginExtremeLocator(null, 5));
        assertThrows(IllegalArgumentException.class,
            () -> new TrailingMarginExtremeLocator(anything -> List.of(), -1));
    }

    /** Filters whatever sits at these positions, as if a locator had found it there. */
    private TrailingMarginExtremeLocator filtering(int margin, int... at) {
        List<Candle> found = IntStream.of(at).mapToObj(candles::get).toList();

        return new TrailingMarginExtremeLocator(ignored -> found, margin);
    }

    private Candle candle(int index) {
        Quotation price = Quotation.of(100 + index);

        return new Candle(1L, new TimePoint(index), price, price, price, price, 0);
    }
}
