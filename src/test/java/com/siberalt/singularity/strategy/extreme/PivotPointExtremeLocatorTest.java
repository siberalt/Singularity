package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

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
}
