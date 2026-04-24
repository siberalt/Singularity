package com.siberalt.singularity.strategy.upside.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaximinUpsideCalculatorTest {

    private ExtremeLocator mockMaxLocator;
    private ExtremeLocator mockMinLocator;
    private MaximinUpsideCalculator calculator;

    @BeforeEach
    void setUp() {
        mockMaxLocator = Mockito.mock(ExtremeLocator.class);
        mockMinLocator = Mockito.mock(ExtremeLocator.class);
        calculator = new MaximinUpsideCalculator(mockMaxLocator, mockMinLocator);
    }

    @Nested
    @DisplayName("Поведение при null или пустом списке")
    class NullOrEmptyInputTests {

        @Test
        @DisplayName("Должен вернуть NEUTRAL при null-свечках")
        void shouldReturnNeutralForNullCandles() {
            Upside result = calculator.calculate(null);
            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при пустом списке")
        void shouldReturnNeutralForEmptyCandles() {
            Upside result = calculator.calculate(List.of());
            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Поведение при отсутствии экстремумов")
    class NoExtremesTests {

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если maxLocator вернул null")
        void shouldReturnNeutralIfMaxLocatorReturnsNull() {
            when(mockMaxLocator.locate(any())).thenReturn(null);
            when(mockMinLocator.locate(any())).thenReturn(List.of(mock(Candle.class)));

            List<Candle> candles = List.of(mock(Candle.class));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если minLocator вернул null")
        void shouldReturnNeutralIfMinLocatorReturnsNull() {
            when(mockMaxLocator.locate(any())).thenReturn(List.of(mock(Candle.class)));
            when(mockMinLocator.locate(any())).thenReturn(null);

            List<Candle> candles = List.of(mock(Candle.class));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если maxLocator вернул пустой список")
        void shouldReturnNeutralIfMaxLocatorReturnsEmpty() {
            when(mockMaxLocator.locate(any())).thenReturn(List.of());
            when(mockMinLocator.locate(any())).thenReturn(List.of(mock(Candle.class)));

            List<Candle> candles = List.of(mock(Candle.class));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если minLocator вернул пустой список")
        void shouldReturnNeutralIfMinLocatorReturnsEmpty() {
            when(mockMaxLocator.locate(any())).thenReturn(List.of(mock(Candle.class)));
            when(mockMinLocator.locate(any())).thenReturn(List.of());

            List<Candle> candles = List.of(mock(Candle.class));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Расчёт upside на основе экстремумов")
    class CalculationTests {

        private Candle lowCandle;
        private Candle highCandle;
        private Candle currentCandle;

        @BeforeEach
        void setupCandles() {
            lowCandle = mock(Candle.class);
            highCandle = mock(Candle.class);
            currentCandle = mock(Candle.class);

            when(lowCandle.getTypicalPriceAsDouble()).thenReturn(100.0);
            when(highCandle.getTypicalPriceAsDouble()).thenReturn(150.0);
        }

        @Test
        @DisplayName("Должен вернуть +1.0, если цена у минимального экстремума")
        void shouldReturnMaxPositiveWhenPriceAtMinimum() {
            // Цена у минимума
            when(currentCandle.getTypicalPriceAsDouble()).thenReturn(100.0);

            when(mockMaxLocator.locate(any())).thenReturn(List.of(highCandle));
            when(mockMinLocator.locate(any())).thenReturn(List.of(lowCandle));

            List<Candle> candles = List.of(currentCandle);
            Upside result = calculator.calculate(candles);

            assertEquals(+1.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("Должен вернуть -1.0, если цена у максимального экстремума")
        void shouldReturnMaxNegativeWhenPriceAtMaximum() {
            // Цена у максимума
            when(currentCandle.getTypicalPriceAsDouble()).thenReturn(150.0);

            when(mockMaxLocator.locate(any())).thenReturn(List.of(highCandle));
            when(mockMinLocator.locate(any())).thenReturn(List.of(lowCandle));

            List<Candle> candles = List.of(currentCandle);
            Upside result = calculator.calculate(candles);

            assertEquals(-1.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("Должен вернуть 0.0, если цена в середине диапазона")
        void shouldReturnZeroWhenPriceInMiddle() {
            // Цена посередине: (100 + 150) / 2 = 125
            when(currentCandle.getTypicalPriceAsDouble()).thenReturn(125.0);

            when(mockMaxLocator.locate(any())).thenReturn(List.of(highCandle));
            when(mockMinLocator.locate(any())).thenReturn(List.of(lowCandle));

            List<Candle> candles = List.of(currentCandle);
            Upside result = calculator.calculate(candles);

            assertEquals(0.0, result.signal(), 0.001);
        }
    }

    @Nested
    @DisplayName("Защита от некорректных данных")
    class EdgeCases {

        @Test
        @DisplayName("Должен вернуть NEUTRAL при NaN в экстремумах")
        void shouldReturnNeutralOnNaNExtremes() {
            Candle extreme = mock(Candle.class);
            when(extreme.getTypicalPriceAsDouble()).thenReturn(Double.NaN);

            when(mockMaxLocator.locate(any())).thenReturn(List.of(extreme));
            when(mockMinLocator.locate(any())).thenReturn(List.of(extreme));

            List<Candle> candles = List.of(mock(Candle.class));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при max <= min")
        void shouldReturnNeutralWhenMaxNotGreaterThanMin() {
            Candle min = mock(Candle.class);
            Candle max = mock(Candle.class);

            when(min.getTypicalPriceAsDouble()).thenReturn(100.0);
            when(max.getTypicalPriceAsDouble()).thenReturn(100.0); // max == min

            when(mockMaxLocator.locate(any())).thenReturn(List.of(max));
            when(mockMinLocator.locate(any())).thenReturn(List.of(min));

            List<Candle> candles = List.of(mock(Candle.class));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }
}