package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvertedUpsideCalculatorTest {

    // Простая заглушка свечи
    /**
     * Вспомогательный метод для создания свечи
     */
    private Candle candle(double close) {
        return Candle.of(TimePoint.NULL, 0, 0, 0, 0, close);
    }

    @Nested
    @DisplayName("Базовое инвертирование")
    class BasicInversion {

        @Test
        @DisplayName("Должен инвертировать signal, сохранить strength")
        void shouldInvertSignalPreserveStrength() {
            // Создаём мок делегата, который вернёт Upside(0.6, 0.8)
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(0.6, 0.8));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            List<Candle> candles = List.of(candle(100.0));
            Upside result = calc.calculate(candles);

            assertEquals(-0.6, result.signal(), 0.001);
            assertEquals(0.8, result.strength(), 0.001);
            verify(mockDelegate).calculate(candles);
        }

        @Test
        @DisplayName("Должен инвертировать положительный сигнал в отрицательный")
        void shouldInvertPositiveSignal() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(0.5, 0.7));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertTrue(result.signal() < 0);
            assertEquals(0.7, result.strength(), 0.001);
        }

        @Test
        @DisplayName("Должен инвертировать отрицательный сигнал в положительный")
        void shouldInvertNegativeSignal() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(-0.3, 0.9));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertTrue(result.signal() > 0);
            assertEquals(0.9, result.strength(), 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При signal = 0 — остаётся 0")
        void shouldKeepZeroSignal() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(0.0, 0.5));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertEquals(0.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("При signal = 1 — инвертируется в -1")
        void shouldInvertMaxSignal() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(1.0, 0.6));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertEquals(-1.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("При signal = -1 — инвертируется в 1")
        void shouldInvertMinSignal() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(-1.0, 0.6));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertEquals(1.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("При null делегата — выбрасывает исключение")
        void shouldThrowOnNullDelegate() {
            assertThrows(NullPointerException.class, () -> new InvertedUpsideCalculator(null));
        }
    }

    @Nested
    @DisplayName("Обработка NEUTRAL")
    class NeutralHandling {

        @Test
        @DisplayName("Если делегат возвращает NEUTRAL — инвертируется в NEUTRAL")
        void shouldInvertNeutral() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(Upside.NEUTRAL);

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertEquals(0.0, result.signal(), 0.001);
            assertEquals(0.0, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При пустом/null списке свечей — делегат вызывается, результат инвертируется")
        void shouldPassThroughEmptyInput() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(null)).thenReturn(new Upside(0.5, 0.6));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(null);

            assertEquals(-0.5, result.signal(), 0.001);
            assertEquals(0.6, result.strength(), 0.001);
            verify(mockDelegate).calculate(null);
        }
    }

    @Nested
    @DisplayName("Инварианты")
    class Invariants {

        @Test
        @DisplayName("Инвертирование дважды — возвращает исходное значение")
        void doubleInversionShouldReturnOriginal() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(0.4, 0.7));

            InvertedUpsideCalculator inverted = new InvertedUpsideCalculator(mockDelegate);
            InvertedUpsideCalculator doubleInverted = new InvertedUpsideCalculator(inverted);

            List<Candle> candles = List.of(candle(100.0));
            Upside original = inverted.calculate(candles);
            Upside doubled = doubleInverted.calculate(candles);

            assertEquals(0.4, doubled.signal(), 0.001);
            assertEquals(0.7, doubled.strength(), 0.001);
        }

        @Test
        @DisplayName("Инвертирование сильного сигнала — сохраняет сильность")
        void strongSignalShouldStayStrong() {
            UpsideCalculator mockDelegate = mock(UpsideCalculator.class);
            when(mockDelegate.calculate(any())).thenReturn(new Upside(0.95, 0.9));

            InvertedUpsideCalculator calc = new InvertedUpsideCalculator(mockDelegate);

            Upside result = calc.calculate(List.of(candle(100.0)));

            assertEquals(-0.95, result.signal(), 0.001);
            assertEquals(0.9, result.strength(), 0.001);
        }
    }
}
