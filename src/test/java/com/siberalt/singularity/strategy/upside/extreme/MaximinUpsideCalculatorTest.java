package com.siberalt.singularity.strategy.upside.extreme;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
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
            when(mockMinLocator.locate(any())).thenReturn(List.of(Candle.EMPTY));

            List<Candle> candles = List.of(Candle.EMPTY);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если minLocator вернул null")
        void shouldReturnNeutralIfMinLocatorReturnsNull() {
            when(mockMaxLocator.locate(any())).thenReturn(List.of(Candle.EMPTY));
            when(mockMinLocator.locate(any())).thenReturn(null);

            List<Candle> candles = List.of(Candle.EMPTY);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если maxLocator вернул пустой список")
        void shouldReturnNeutralIfMaxLocatorReturnsEmpty() {
            when(mockMaxLocator.locate(any())).thenReturn(List.of());
            when(mockMinLocator.locate(any())).thenReturn(List.of(Candle.EMPTY));

            List<Candle> candles = List.of(Candle.EMPTY);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL, если minLocator вернул пустой список")
        void shouldReturnNeutralIfMinLocatorReturnsEmpty() {
            when(mockMaxLocator.locate(any())).thenReturn(List.of(Candle.EMPTY));
            when(mockMinLocator.locate(any())).thenReturn(List.of());

            List<Candle> candles = List.of(Candle.EMPTY);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Расчёт upside на основе экстремумов")
    class CalculationTests {

        private Candle lowCandle;
        private Candle highCandle;

        @BeforeEach
        void setupCandles() {
            lowCandle = Candle.builder().setClose(Quotation.of(100.0)).build();
            highCandle = Candle.builder().setClose(Quotation.of(150.0)).build();
        }

        @Test
        @DisplayName("Должен вернуть +1.0, если цена у минимального экстремума")
        void shouldReturnMaxPositiveWhenPriceAtMinimum() {
            // Цена у минимума
            Candle currentCandle = Candle.builder().setClose(Quotation.of(100.0)).build();

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
            Candle currentCandle = Candle.builder().setClose(Quotation.of(150.0)).build();

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
            Candle currentCandle = Candle.builder().setClose(Quotation.of(125.0)).build();

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
        @DisplayName("Должен вернуть NEUTRAL при отсутствии валидных экстремумов")
        void shouldReturnNeutralWhenNoValidExtremes() {
            // Мокаем локаторы так, чтобы они возвращали пустые списки
            when(mockMaxLocator.locate(any())).thenReturn(Collections.emptyList());
            when(mockMinLocator.locate(any())).thenReturn(Collections.emptyList());

            List<Candle> candles = List.of(Candle.EMPTY);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при max <= min")
        void shouldReturnNeutralWhenMaxNotGreaterThanMin() {
            Candle min = Candle.builder().setClose(Quotation.of(100.0)).build();
            Candle max = Candle.builder().setClose(Quotation.of(100.0)).build();

            when(mockMaxLocator.locate(any())).thenReturn(List.of(max));
            when(mockMinLocator.locate(any())).thenReturn(List.of(min));

            List<Candle> candles = List.of(Candle.EMPTY);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }
}