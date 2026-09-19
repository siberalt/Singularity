package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProminentExtremeLocatorTest {
    private static final Instant START = Instant.parse("2023-01-02T07:00:00Z");

    /** Every candle is an extreme, so the test decides what the decorator is asked to judge. */
    private static final ExtremeLocator ALL = candles -> candles;

    @Test
    void keepsTheDeepDipAndDropsTheShallowOne() {
        // A dip of ten from a plateau of a hundred, and a dip of one.
        List<Candle> candles = series(100, 100, 90, 100, 100, 99, 100, 100);
        ExtremeLocator locator = ProminentExtremeLocator.ofMinimums(ALL, window -> 2, 1);

        List<Candle> prominent = locator.locate(candles);

        assertEquals(List.of(2L), prominent.stream().map(Candle::getIndex).toList());
    }

    @Test
    void measuresProminenceInVolatilityRatherThanInPrice() {
        List<Candle> candles = series(100, 100, 96, 100, 100);

        // A dip of four is prominent when a volatility is two, and is not when a volatility is ten.
        assertEquals(1, ProminentExtremeLocator.ofMinimums(ALL, window -> 2, 1).locate(candles).size());
        assertEquals(0, ProminentExtremeLocator.ofMinimums(ALL, window -> 10, 1).locate(candles).size());
    }

    /**
     * Prominence is the smaller of the two rises, so a low with a wall on one side and nothing on the
     * other is not prominent - it is the edge of a slope, not the bottom of a dip.
     */
    @Test
    void takesTheSmallerOfTheTwoSides() {
        // Left rises by twenty, right by one before a lower low appears.
        List<Candle> candles = series(120, 100, 101, 99, 90);
        List<Candle> prominent = ProminentExtremeLocator.ofMinimums(ALL, window -> 2, 5).locate(candles);

        assertTrue(prominent.stream().map(Candle::getIndex).noneMatch(index -> index == 1L),
            "the low at 100 has only one high side and should not count as a deep dip");
    }

    @Test
    void aLowAtTheEdgeOfTheWindowCountsOnlyWhatItHasShown() {
        // The last candle is the lowest: to the right there is nothing yet, so it has no prominence.
        List<Candle> candles = series(100, 100, 95, 100, 90);
        List<Candle> prominent = ProminentExtremeLocator.ofMinimums(ALL, window -> 2, 1).locate(candles);

        assertEquals(List.of(2L), prominent.stream().map(Candle::getIndex).toList());
    }

    @Test
    void worksTheOtherWayRoundForMaximums() {
        // A peak of ten above the plateau, and one of a single point.
        List<Candle> candles = series(100, 100, 110, 100, 100, 101, 100, 100);
        List<Candle> prominent = ProminentExtremeLocator.ofMaximums(ALL, window -> 2, 1).locate(candles);

        assertEquals(List.of(2L), prominent.stream().map(Candle::getIndex).toList());
    }

    @Test
    void keepsWhatItWasGivenWhenThereIsNothingToMeasureBy() {
        List<Candle> candles = series(100, 90, 100);

        // A volatility of zero means the window is too short for it: filtering at random would be worse
        // than not filtering.
        assertEquals(3, ProminentExtremeLocator.ofMinimums(ALL, window -> 0, 1).locate(candles).size());
        assertTrue(ProminentExtremeLocator.ofMinimums(ALL, window -> 2, 1).locate(List.of()).isEmpty());
    }

    @Test
    void passesThroughOnlyWhatTheBaseLocatorFound() {
        List<Candle> candles = series(100, 100, 90, 100, 100);
        ExtremeLocator nothing = window -> List.of();

        assertTrue(ProminentExtremeLocator.ofMinimums(nothing, window -> 2, 1).locate(candles).isEmpty());
    }

    /**
     * Which prices the depth is read off is a setting, not something baked in: the maximum variant is
     * the minimum one with negated prices, and any other pair of prices works the same way.
     */
    @Test
    void readsWhateverPricesItIsGiven() {
        List<Candle> candles = new ArrayList<>();

        // Every bar spans 90 to 100, and the closes dip in the middle. Read off the lows, every bar is a
        // ten-deep dip and all of them pass; read off the closes, only the middle one is.
        double[] closes = {100, 100, 94, 100, 100};

        for (int at = 0; at < closes.length; at++) {
            candles.add(new Candle(1, new TimePoint(at, START.plusSeconds(3600L * at)),
                Quotation.of(closes[at]), Quotation.of(closes[at]), Quotation.of(100), Quotation.of(90), 1));
        }

        // All but the two at the edges, which have nothing on one side of them to rise from.
        assertEquals(List.of(1L, 2L, 3L), ProminentExtremeLocator.ofMinimums(ALL, window -> 2, 1)
            .locate(candles)
            .stream()
            .map(Candle::getIndex)
            .toList());

        ExtremeLocator byClose = new ProminentExtremeLocator(
            ALL, window -> 2, 1, Candle::close, Candle::close);

        assertEquals(List.of(2L), byClose.locate(candles).stream().map(Candle::getIndex).toList());
    }

    @Test
    void refusesAThresholdThatCannotFilterAnything() {
        assertThrows(IllegalArgumentException.class, () -> ProminentExtremeLocator.ofMinimums(ALL, window -> 1, 0));
        assertThrows(IllegalArgumentException.class, () -> ProminentExtremeLocator.ofMinimums(null, window -> 1, 1));
    }

    /** Candles whose high, low and close are the same price, one an hour apart, numbered from zero. */
    private static List<Candle> series(double... prices) {
        List<Candle> candles = new ArrayList<>();

        for (int at = 0; at < prices.length; at++) {
            Quotation price = Quotation.of(prices[at]);
            candles.add(new Candle(1, new TimePoint(at, START.plusSeconds(3600L * at)), price, price, price, price, 1));
        }

        return candles;
    }
}
