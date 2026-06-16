package com.siberalt.singularity.strategy.upside;

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
 * Тесты для ThresholdSwitchUpsideCalculator.
 */
class ThresholdSwitchUpsideCalculatorTest {

    private UpsideCalculator mockCalculatorA;
    private UpsideCalculator mockCalculatorB;
    private ThresholdSwitchUpsideCalculator calculator;

    @BeforeEach
    void setUp() {
        mockCalculatorA = mock(UpsideCalculator.class);
        mockCalculatorB = mock(UpsideCalculator.class);
        calculator = new ThresholdSwitchUpsideCalculator(mockCalculatorA, mockCalculatorB);
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {

        @Test
        @DisplayName("Должен создавать с кастомными порогами")
        void shouldCreateWithCustomThresholds() {
            ThresholdSwitchUpsideCalculator customCalculator = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.7, -0.7
            );

            assertNotNull(customCalculator);
            assertEquals(0.7, customCalculator.topThreshold(), 0.001);
            assertEquals(-0.7, customCalculator.bottomThreshold(), 0.001);
        }

        @Test
        @DisplayName("Должен создавать с порогами по умолчанию")
        void shouldCreateWithDefaultThresholds() {
            ThresholdSwitchUpsideCalculator defaultCalculator = new ThresholdSwitchUpsideCalculator(
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
                new ThresholdSwitchUpsideCalculator(null, mockCalculatorB)
            );

            assertTrue(exception.getMessage().contains("CalculatorA"));
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при null calculatorB")
        void shouldThrowOnNullCalculatorB() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new ThresholdSwitchUpsideCalculator(mockCalculatorA, null)
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
            Upside upsideA = new Upside(0.7, 0.8); // > topThreshold (0.5)
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }

        @Test
        @DisplayName("Должен возвращать сигнал от A, когда signalA < bottomThreshold")
        void shouldReturnASignalWhenBelowBottomThreshold() {
            Upside upsideA = new Upside(-0.7, 0.8); // < bottomThreshold (-0.5)
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }

        @Test
        @DisplayName("Должен возвращать сигнал от A при signalA == topThreshold (на границе)")
        void shouldReturnASignalWhenSignalAtTopThreshold() {
            Upside upsideA = new Upside(0.5, 0.8); // == topThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideA, result);
            verify(mockCalculatorA).calculate(candles);
            verifyNoInteractions(mockCalculatorB);
        }

        @Test
        @DisplayName("Должен возвращать сигнал от A при signalA == bottomThreshold (на границе)")
        void shouldReturnASignalWhenSignalAtBottomThreshold() {
            Upside upsideA = new Upside(-0.5, 0.8); // == bottomThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideA, result);
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
            Upside upsideA = new Upside(0.2, 0.4);
            Upside upsideB = new Upside(0.6, 0.7);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);
            when(mockCalculatorB.calculate(any())).thenReturn(upsideB);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideB, result);
            verify(mockCalculatorA).calculate(candles);
            verify(mockCalculatorB).calculate(candles);
        }

        @Test
        @DisplayName("Должен делегировать на B с теми же свечами")
        void shouldDelegateToBWithSameCandles() {
            Upside upsideA = new Upside(0.3, 0.5);
            Upside upsideB = new Upside(0.8, 0.9);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);
            when(mockCalculatorB.calculate(any())).thenReturn(upsideB);

            List<Candle> testCandles = List.of();
            calculator.calculate(testCandles);

            verify(mockCalculatorB).calculate(eq(testCandles));
        }

        @Test
        @DisplayName("Должен корректно обрабатывать отрицательный сигнал внутри порогов")
        void shouldReturnBSignalForNegativeAWithinThreshold() {
            Upside upsideA = new Upside(-0.3, 0.6);
            Upside upsideB = new Upside(-0.7, 0.8);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);
            when(mockCalculatorB.calculate(any())).thenReturn(upsideB);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideB, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать нулевой сигнал от A")
        void shouldReturnBSignalForZeroSignalA() {
            Upside upsideA = Upside.NEUTRAL;
            Upside upsideB = new Upside(0.5, 0.6);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);
            when(mockCalculatorB.calculate(any())).thenReturn(upsideB);

            List<Candle> candles = List.of();
            Upside result = calculator.calculate(candles);

            assertEquals(upsideB, result);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Должен работать с нулевыми свечами (пустой список)")
        void shouldHandleEmptyCandles() {
            Upside upsideA = new Upside(0.6, 0.7);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            Upside result = calculator.calculate(List.of());

            assertEquals(upsideA, result);
        }

        @Test
        @DisplayName("Должен работать с null-списком свечей")
        void shouldHandleNullCandles() {
            Upside upsideA = new Upside(0.6, 0.7);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            Upside result = calculator.calculate(null);

            assertEquals(upsideA, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать сигналы на границах с отрицательным A")
        void shouldHandleSignalAtNegativeBoundary() {
            ThresholdSwitchUpsideCalculator calc = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.6, -0.4
            );

            Upside upsideA = new Upside(-0.4, 0.5); // == bottomThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = calc.calculate(candles);

            assertEquals(upsideA, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать сигналы на границах с положительным A")
        void shouldHandleSignalAtPositiveBoundary() {
            ThresholdSwitchUpsideCalculator calc = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.6, -0.4
            );

            Upside upsideA = new Upside(0.6, 0.5); // == topThreshold
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = calc.calculate(candles);

            assertEquals(upsideA, result);
        }
    }

    @Nested
    @DisplayName("Работа с кастомными порогами")
    class CustomThresholds {

        @Test
        @DisplayName("Должен использовать кастомные пороги для переключения")
        void shouldUseCustomThresholds() {
            ThresholdSwitchUpsideCalculator customCalc = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.7, -0.7
            );

            // Сигнал 0.6 находится между -0.7 и 0.7, поэтому должен использовать B
            Upside upsideA = new Upside(0.6, 0.8);
            Upside upsideB = new Upside(0.9, 0.95);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);
            when(mockCalculatorB.calculate(any())).thenReturn(upsideB);

            List<Candle> candles = List.of();
            Upside result = customCalc.calculate(candles);

            assertEquals(upsideB, result);
        }

        @Test
        @DisplayName("Должен переключаться на A при превышении кастомного topThreshold")
        void shouldSwitchToAWhenExceedingCustomTopThreshold() {
            ThresholdSwitchUpsideCalculator customCalc = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.6, -0.6
            );

            // Сигнал 0.7 > 0.6, поэтому должен использовать A
            Upside upsideA = new Upside(0.7, 0.85);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = customCalc.calculate(candles);

            assertEquals(upsideA, result);
        }

        @Test
        @DisplayName("Должен переключаться на A при превышении кастомного bottomThreshold")
        void shouldSwitchToAWhenBelowCustomBottomThreshold() {
            ThresholdSwitchUpsideCalculator customCalc = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.6, -0.6
            );

            // Сигнал -0.7 < -0.6, поэтому должен использовать A
            Upside upsideA = new Upside(-0.7, 0.85);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);

            List<Candle> candles = List.of();
            Upside result = customCalc.calculate(candles);

            assertEquals(upsideA, result);
        }

        @Test
        @DisplayName("Должен корректно работать с несимметричными порогами")
        void shouldHandleAsymmetricThresholds() {
            ThresholdSwitchUpsideCalculator asymmetricCalc = new ThresholdSwitchUpsideCalculator(
                mockCalculatorA, mockCalculatorB, 0.8, -0.3
            );

            // Сигнал -0.2 находится между -0.3 и 0.8, поэтому должен использовать B
            Upside upsideA = new Upside(-0.2, 0.5);
            Upside upsideB = new Upside(0.7, 0.8);
            when(mockCalculatorA.calculate(any())).thenReturn(upsideA);
            when(mockCalculatorB.calculate(any())).thenReturn(upsideB);

            List<Candle> candles = List.of();
            Upside result = asymmetricCalc.calculate(candles);

            assertEquals(upsideB, result);
        }
    }
}
