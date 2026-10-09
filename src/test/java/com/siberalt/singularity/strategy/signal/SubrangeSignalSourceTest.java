package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.shared.RangeInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SubrangeSignalSourceTest {

    private SignalSource mockBaseCalculator;
    private List<Candle> candles;

    @BeforeEach
    void setUp() {
        mockBaseCalculator = mock(SignalSource.class);
        CandleFactory candleFactory = new CandleFactory(1L);

        candles = List.of(
            candleFactory.createCommon("2021-01-01T00:01:00Z", 100.0),
            candleFactory.createCommon("2021-01-01T00:02:00Z", 105.0),
            candleFactory.createCommon("2021-01-01T00:03:00Z", 110.0)
        );
    }

    @Nested
    @DisplayName("Конструктор и базовое поведение")
    class ConstructorTests {

        @Test
        @DisplayName("Должен применять диапазон и делегировать вычисление базовому калькулятору")
        void shouldApplyRangeAndDelegateCalculation() {
            // Создаём функцию, которая выбирает [1, 3) — последние две свечи
            Function<List<Candle>, RangeInt> rangeFunction = list -> new RangeInt(1, 2);
            SubrangeSignalSource calculator = new SubrangeSignalSource(rangeFunction, mockBaseCalculator);

            Signal expected = new Signal(0.5, 0.5);
            when(mockBaseCalculator.calculate(candles.subList(1, 2))).thenReturn(expected);

            Signal result = calculator.calculate(candles);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при null-списке, если baseCalculator возвращает NEUTRAL")
        void shouldHandleNullCandles() {
            SubrangeSignalSource calculator = SubrangeSignalSource.ofLastN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(Signal.NEUTRAL);

            Signal result = calculator.calculate(null);

            assertEquals(Signal.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при пустом списке")
        void shouldHandleEmptyCandles() {
            SubrangeSignalSource calculator = SubrangeSignalSource.ofLastN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(Signal.NEUTRAL);

            Signal result = calculator.calculate(List.of());

            assertEquals(Signal.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Фабричный метод ofLastN")
    class OfLastNTests {

        @Test
        @DisplayName("Должен выбирать последние N свечей")
        void shouldSelectLastNElements() {
            Candle candle2 = candles.get(1);
            Candle candle3 = candles.get(2);
            SubrangeSignalSource calculator = SubrangeSignalSource.ofLastN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Signal(0.6, 0.7));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list ->
                list.size() == 2 && list.get(0) == candle2 && list.get(1) == candle3
            ));
        }

        @Test
        @DisplayName("Должен выбирать все свечи, если N больше размера списка")
        void shouldSelectAllWhenNExceedsSize() {
            Candle candle1 = candles.get(0);
            SubrangeSignalSource calculator = SubrangeSignalSource.ofLastN(5, mockBaseCalculator, true);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Signal(0.7, 0.8));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list -> list.size() == 3 && list.get(0) == candle1));
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при отрицательном N")
        void shouldThrowOnNegativeN() {
            assertThrows(IllegalArgumentException.class, () ->
                SubrangeSignalSource.ofLastN(-1, mockBaseCalculator)
            );
        }
    }

    @Nested
    @DisplayName("Фабричный метод ofFirstN")
    class OfFirstNTests {

        @Test
        @DisplayName("Должен выбирать первые N свечей")
        void shouldSelectFirstNElements() {
            Candle candle1 = candles.get(0);
            Candle candle2 = candles.get(1);
            SubrangeSignalSource calculator = SubrangeSignalSource.ofFirstN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Signal(0.4, 0.5));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list ->
                list.size() == 2 && list.get(0) == candle1 && list.get(1) == candle2
            ));
        }

        @Test
        @DisplayName("Должен выбирать все свечи, если N больше размера списка")
        void shouldSelectAllWhenNExceedsSize() {
            Candle candle1 = candles.get(0);
            SubrangeSignalSource calculator = SubrangeSignalSource.ofFirstN(5, mockBaseCalculator, true);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Signal(0.8, 0.9));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list -> list.size() == 3 && list.get(0) == candle1));
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при отрицательном N")
        void shouldThrowOnNegativeN() {
            assertThrows(IllegalArgumentException.class, () ->
                SubrangeSignalSource.ofFirstN(-1, mockBaseCalculator)
            );
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Должен корректно обрабатывать список из одной свечи")
        void shouldHandleSingleCandle() {
            Candle candle1 = candles.get(0);
            List<Candle> single = List.of(candle1);
            SubrangeSignalSource calculator = SubrangeSignalSource.ofLastN(1, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Signal(0.5, 0.5));

            calculator.calculate(single);

            verify(mockBaseCalculator).calculate(argThat(list -> list.size() == 1 && list.get(0) == candle1));
        }

        @Test
        @DisplayName("Должен возвращать пустой подсписок при n=0")
        void shouldReturnEmptySublistForNZero() {
            SubrangeSignalSource calculator = SubrangeSignalSource.ofLastN(0, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(Signal.NEUTRAL);

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(List::isEmpty));
        }
    }
}