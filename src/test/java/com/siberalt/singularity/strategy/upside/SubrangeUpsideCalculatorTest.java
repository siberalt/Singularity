package com.siberalt.singularity.strategy.upside;

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

class SubrangeUpsideCalculatorTest {

    private UpsideCalculator mockBaseCalculator;
    private List<Candle> candles;

    @BeforeEach
    void setUp() {
        mockBaseCalculator = mock(UpsideCalculator.class);
        CandleFactory candleFactory = new CandleFactory("TEST");

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
            SubrangeUpsideCalculator calculator = new SubrangeUpsideCalculator(rangeFunction, mockBaseCalculator);

            Upside expected = new Upside(0.5, 0.5);
            when(mockBaseCalculator.calculate(candles.subList(1, 2))).thenReturn(expected);

            Upside result = calculator.calculate(candles);

            assertEquals(expected, result);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при null-списке, если baseCalculator возвращает NEUTRAL")
        void shouldHandleNullCandles() {
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofLastN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(Upside.NEUTRAL);

            Upside result = calculator.calculate(null);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при пустом списке")
        void shouldHandleEmptyCandles() {
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofLastN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(Upside.NEUTRAL);

            Upside result = calculator.calculate(List.of());

            assertEquals(Upside.NEUTRAL, result);
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
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofLastN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Upside(0.6, 0.7));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list ->
                list.size() == 2 && list.get(0) == candle2 && list.get(1) == candle3
            ));
        }

        @Test
        @DisplayName("Должен выбирать все свечи, если N больше размера списка")
        void shouldSelectAllWhenNExceedsSize() {
            Candle candle1 = candles.get(0);
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofLastN(5, mockBaseCalculator, true);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Upside(0.7, 0.8));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list -> list.size() == 3 && list.get(0) == candle1));
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при отрицательном N")
        void shouldThrowOnNegativeN() {
            assertThrows(IllegalArgumentException.class, () ->
                SubrangeUpsideCalculator.ofLastN(-1, mockBaseCalculator)
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
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofFirstN(2, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Upside(0.4, 0.5));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list ->
                list.size() == 2 && list.get(0) == candle1 && list.get(1) == candle2
            ));
        }

        @Test
        @DisplayName("Должен выбирать все свечи, если N больше размера списка")
        void shouldSelectAllWhenNExceedsSize() {
            Candle candle1 = candles.get(0);
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofFirstN(5, mockBaseCalculator, true);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Upside(0.8, 0.9));

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(list -> list.size() == 3 && list.get(0) == candle1));
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при отрицательном N")
        void shouldThrowOnNegativeN() {
            assertThrows(IllegalArgumentException.class, () ->
                SubrangeUpsideCalculator.ofFirstN(-1, mockBaseCalculator)
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
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofLastN(1, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(new Upside(0.5, 0.5));

            calculator.calculate(single);

            verify(mockBaseCalculator).calculate(argThat(list -> list.size() == 1 && list.get(0) == candle1));
        }

        @Test
        @DisplayName("Должен возвращать пустой подсписок при n=0")
        void shouldReturnEmptySublistForNZero() {
            SubrangeUpsideCalculator calculator = SubrangeUpsideCalculator.ofLastN(0, mockBaseCalculator);
            when(mockBaseCalculator.calculate(any())).thenReturn(Upside.NEUTRAL);

            calculator.calculate(candles);

            verify(mockBaseCalculator).calculate(argThat(List::isEmpty));
        }
    }
}