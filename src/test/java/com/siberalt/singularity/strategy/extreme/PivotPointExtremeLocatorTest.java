package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class PivotPointExtremeLocatorTest {

    // Заглушка Candle с фиксированными close
    private Candle candle(double close) {
        return Candle.of(TimePoint.NULL, 0, 0, 0, 0, close);
    }

    @Nested
    @DisplayName("Базовые случаи с vicinity = (1,1)")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Vicinity1x1 {
        @Test
        void shouldDetectPeak() {
            PivotPointExtremeLocator locator = PivotPointExtremeLocator.ofMaximums(1);
            List<Candle> candles = List.of(
                candle(10.0),
                candle(12.0), // peak
                candle(11.0)
            );

            List<Candle> extremes = locator.locate(candles);
            assertEquals(1, extremes.size());
            assertSame(candles.get(1), extremes.get(0));
        }

        @Test
        void shouldDetectTrough() {
            PivotPointExtremeLocator locator = PivotPointExtremeLocator.ofMinimums(1);
            List<Candle> candles = List.of(
                candle(8.0),
                candle(6.0), // trough
                candle(7.0)
            );

            List<Candle> extremes = locator.locate(candles);
            assertEquals(1, extremes.size());
            assertSame(candles.get(1), extremes.get(0));
        }
    }

    @Nested
    @DisplayName("Работа с разными окрестностями")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CustomVicinities {
        @Test
        void shouldDetectSignificantExtremumWithVicinity2x2() {
            List<Candle> candles = List.of(
                candle(10.0),
                candle(11.0),
                candle(13.0), // candidate peak
                candle(12.0),
                candle(11.0)
            );
            // The high at index 2 is the maximum within the [0..4] window

            PivotPointExtremeLocator locator = PivotPointExtremeLocator.ofMaximums(2);
            List<Candle> extremes = locator.locate(candles);

            assertEquals(1, extremes.size());
            assertSame(candles.get(2), extremes.get(0));
        }

        @Test
        void shouldReturnEmptyListWhenInputIsTooSmall() {
            List<Candle> candles = List.of(
                candle(10.0),
                candle(11.0)
            );

            PivotPointExtremeLocator locator = PivotPointExtremeLocator.ofMaximums(2);
            List<Candle> extremes = locator.locate(candles);

            assertTrue(extremes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {
        private final PivotPointExtremeLocator locator = PivotPointExtremeLocator.ofMaximums();

        @Test
        void shouldReturnEmptyListWhenInputIsNull() {
            List<Candle> result = locator.locate(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyListWhenInputIsTooSmall() {
            List<Candle> candles = List.of(candle(1.0));
            List<Candle> result = locator.locate(candles);
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyListWhenNoExtremesPresent() {
            List<Candle> candles = List.of(
                candle(10.0),
                candle(11.0),
                candle(12.0), // upward trend
                candle(13.0)
            );

            List<Candle> result = locator.locate(candles);
            assertTrue(result.isEmpty());
        }
    }

    /**
     * What depends on where candles sit. The cases above build every candle at index -1, which is
     * why none of this was ever checked: grouping by position had nothing to go on.
     */
    @Nested
    class Placement {
        private static final Comparator<Candle> MINIMUMS = Comparator.comparingDouble(Candle::getCloseAsDouble);

        @Test
        void takesOnlyTheFirstCandleOfAPlateau() {
            List<Candle> candles = placed(1, 5, 2, 2, 5);

            assertEquals(List.of(candles.get(1)), new PivotPointExtremeLocator(MINIMUMS, 1, 1, 0).locate(candles));
        }

        @Test
        void keepsTheDeepestOfPivotsCloseTogether() {
            List<Candle> candles = placed(1, 5, 3, 5, 1, 5);

            assertEquals(List.of(candles.get(3)), PivotPointExtremeLocator.ofMinimums(1).locate(candles));
        }

        /** Rolled-up bars sit sixty index units apart and are still neighbours. */
        @Test
        void countsTheGroupingReachInBars() {
            List<Candle> candles = placed(60, 5, 3, 5, 1, 5);

            assertEquals(List.of(candles.get(3)), PivotPointExtremeLocator.ofMinimums(1).locate(candles));
        }

        @Test
        void reportsEveryPivotWithoutGrouping() {
            List<Candle> candles = placed(1, 5, 3, 5, 1, 5);

            assertEquals(
                List.of(candles.get(1), candles.get(3)),
                PivotPointExtremeLocator.ofMinimums(1).withoutGrouping().locate(candles)
            );
        }

        /** Split in two and put back together, the locator answers as it does whole. */
        @Test
        void answersAsItselfWhenItsGroupingIsLaidOverItsPivots() {
            List<Candle> candles = placed(1, 5, 3, 5, 1, 5, 4, 6, 2, 6);
            PivotPointExtremeLocator locator = PivotPointExtremeLocator.ofMinimums(1);

            assertEquals(locator.locate(candles), locator.groupingOf(locator.withoutGrouping()).locate(candles));
        }

        private List<Candle> placed(long step, double... closes) {
            return IntStream.range(0, closes.length)
                .mapToObj(i -> {
                    Quotation price = Quotation.of(closes[i]);

                    return new Candle(1L, new TimePoint(i * step), price, price, price, price, 0);
                })
                .toList();
        }
    }
}
