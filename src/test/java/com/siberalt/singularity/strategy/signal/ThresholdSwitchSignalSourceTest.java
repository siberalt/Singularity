package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для ThresholdSwitchSignalSource.
 */
class ThresholdSwitchSignalSourceTest {

    private SignalSource mockCalculatorA;
    private SignalSource mockCalculatorB;
    private ThresholdSwitchSignalSource calculator;

    @BeforeEach
    void setUp() {
        mockCalculatorA = mock(SignalSource.class);
        mockCalculatorB = mock(SignalSource.class);
        calculator = new ThresholdSwitchSignalSource(mockCalculatorA, mockCalculatorB);
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {

        @Test
        @DisplayName("Должен создавать с кастомными порогами")
        void shouldCreateWithCustomThresholds() {
            ThresholdSwitchSignalSource customCalculator = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.7, -0.7
            );

            assertNotNull(customCalculator);
            assertEquals(0.7, customCalculator.topThreshold(), 0.001);
            assertEquals(-0.7, customCalculator.bottomThreshold(), 0.001);
        }

        @Test
        @DisplayName("Должен создавать с порогами по умолчанию")
        void shouldCreateWithDefaultThresholds() {
            ThresholdSwitchSignalSource defaultCalculator = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB
            );

            assertNotNull(defaultCalculator);
            assertEquals(0.5, defaultCalculator.topThreshold(), 0.001);
            assertEquals(-0.5, defaultCalculator.bottomThreshold(), 0.001);
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при null calculatorA")
        void shouldThrowOnNullCalculatorA() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ThresholdSwitchSignalSource(null, mockCalculatorB)
            );

            assertTrue(exception.getMessage().contains("CalculatorA"));
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при null calculatorB")
        void shouldThrowOnNullCalculatorB() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ThresholdSwitchSignalSource(mockCalculatorA, null)
            );

            assertTrue(exception.getMessage().contains("CalculatorB"));
        }
    }

    @Nested
    @DisplayName("Поведение при выходе за пороги")
    class OutOfThresholdBehavior {

        @Test
        @DisplayName("Должен возвращать сигнал от A, когда signalA > topThreshold")
        void shouldReturnASignalWhenAboveTopThreshold() {
            Signal signalA = new Signal(0.7, 0.8); // > topThreshold (0.5)
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }

        @Test
        @DisplayName("Должен возвращать сигнал от A, когда signalA < bottomThreshold")
        void shouldReturnASignalWhenBelowBottomThreshold() {
            Signal signalA = new Signal(-0.7, 0.8); // < bottomThreshold (-0.5)
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }

        @Test
        @DisplayName("Должен возвращать сигнал от A при signalA == topThreshold (на границе)")
        void shouldReturnASignalWhenSignalAtTopThreshold() {
            Signal signalA = new Signal(0.5, 0.8); // == topThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }

        @Test
        @DisplayName("Должен возвращать сигнал от A при signalA == bottomThreshold (на границе)")
        void shouldReturnASignalWhenSignalAtBottomThreshold() {
            Signal signalA = new Signal(-0.5, 0.8); // == bottomThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }
    }

    @Nested
    @DisplayName("Поведение внутри порогов")
    class WithinThresholdBehavior {

        @Test
        @DisplayName("Должен возвращать сигнал от B, когда A в пределах порогов")
        void shouldReturnBSignalWhenAWithinThresholds() {
            // Сигнал A внутри диапазона [-0.5, 0.5]
            Signal signalA = new Signal(0.2, 0.4);
            Signal signalB = new Signal(0.6, 0.7);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);
            when(mockCalculatorB.calculate(any())).thenReturn(signalB);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalB, result);
            verify(mockCalculatorA).calculate(candles);
            verify(mockCalculatorB).calculate(candles);
        }

        @Test
        @DisplayName("Должен делегировать на B с теми же свечами")
        void shouldDelegateToBWithSameCandles() {
            Signal signalA = new Signal(0.3, 0.5);
            Signal signalB = new Signal(0.8, 0.9);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);
            when(mockCalculatorB.calculate(any())).thenReturn(signalB);

            List<Candle> testCandles = List.of();
            calculator.calculate(testCandles);

            verify(mockCalculatorB).calculate(eq(testCandles));
        }

        @Test
        @DisplayName("Должен корректно обрабатывать отрицательный сигнал внутри порогов")
        void shouldReturnBSignalForNegativeAWithinThreshold() {
            Signal signalA = new Signal(-0.3, 0.6);
            Signal signalB = new Signal(-0.7, 0.8);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);
            when(mockCalculatorB.calculate(any())).thenReturn(signalB);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalB, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать нулевой сигнал от A")
        void shouldReturnBSignalForZeroSignalA() {
            Signal signalA = Signal.NEUTRAL;
            Signal signalB = new Signal(0.5, 0.6);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);
            when(mockCalculatorB.calculate(any())).thenReturn(signalB);

            List<Candle> candles = List.of();
            Signal result = calculator.calculate(candles);

            assertEquals(signalB, result);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Должен работать с нулевыми свечами (пустой список)")
        void shouldHandleEmptyCandles() {
            Signal signalA = new Signal(0.6, 0.7);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            Signal result = calculator.calculate(List.of());

            assertEquals(signalA, result);
        }

        @Test
        @DisplayName("Должен работать с null-списком свечей")
        void shouldHandleNullCandles() {
            Signal signalA = new Signal(0.6, 0.7);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            Signal result = calculator.calculate(null);

            assertEquals(signalA, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать сигналы на границах с отрицательным A")
        void shouldHandleSignalAtNegativeBoundary() {
            ThresholdSwitchSignalSource calc = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.6, -0.4
            );

            Signal signalA = new Signal(-0.4, 0.5); // == bottomThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = calc.calculate(candles);

            assertEquals(signalA, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать сигналы на границах с положительным A")
        void shouldHandleSignalAtPositiveBoundary() {
            ThresholdSwitchSignalSource calc = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.6, -0.4
            );

            Signal signalA = new Signal(0.6, 0.5); // == topThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = calc.calculate(candles);

            assertEquals(signalA, result);
        }
    }

    @Nested
    @DisplayName("Работа с кастомными порогами")
    class CustomThresholds {

        @Test
        @DisplayName("Должен использовать кастомные пороги для переключения")
        void shouldUseCustomThresholds() {
            ThresholdSwitchSignalSource customCalc = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.7, -0.7
            );

            // Сигнал 0.6 находится между -0.7 и 0.7, поэтому должен использовать B
            Signal signalA = new Signal(0.6, 0.8);
            Signal signalB = new Signal(0.9, 0.95);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);
            when(mockCalculatorB.calculate(any())).thenReturn(signalB);

            List<Candle> candles = List.of();
            Signal result = customCalc.calculate(candles);

            assertEquals(signalB, result);
        }

        @Test
        @DisplayName("Должен переключаться на A при превышении кастомного topThreshold")
        void shouldSwitchToAWhenExceedingCustomTopThreshold() {
            ThresholdSwitchSignalSource customCalc = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.6, -0.6
            );

            // Сигнал 0.7 > 0.6, поэтому должен использовать A
            Signal signalA = new Signal(0.7, 0.85);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = customCalc.calculate(candles);

            assertEquals(signalA, result);
        }

        @Test
        @DisplayName("Должен переключаться на A при превышении кастомного bottomThreshold")
        void shouldSwitchToAWhenBelowCustomBottomThreshold() {
            ThresholdSwitchSignalSource customCalc = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.6, -0.6
            );

            // Сигнал -0.7 < -0.6, поэтому должен использовать A
            Signal signalA = new Signal(-0.7, 0.85);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);

            List<Candle> candles = List.of();
            Signal result = customCalc.calculate(candles);

            assertEquals(signalA, result);
        }

        @Test
        @DisplayName("Должен корректно работать с несимметричными порогами")
        void shouldHandleAsymmetricThresholds() {
            ThresholdSwitchSignalSource asymmetricCalc = new ThresholdSwitchSignalSource(
                mockCalculatorA, mockCalculatorB, 0.8, -0.3
            );

            // Сигнал -0.2 находится между -0.3 и 0.8, поэтому должен использовать B
            Signal signalA = new Signal(-0.2, 0.5);
            Signal signalB = new Signal(0.7, 0.8);
            when(mockCalculatorA.calculate(any())).thenReturn(signalA);
            when(mockCalculatorB.calculate(any())).thenReturn(signalB);

            List<Candle> candles = List.of();
            Signal result = asymmetricCalc.calculate(candles);

            assertEquals(signalB, result);
        }
    }
}
