package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpsideSignalAmplifierTest {

    private static final List<Candle> EMPTY_CANDLES = List.of();

    @Mock
    private UpsideCalculator delegate;

    // === Constructor validation tests ===

    @Test
    @DisplayName("Бросает исключение при null делегате")
    void testNullDelegateThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new UpsideSignalAmplifier(null, 0.5, 0.5));
    }

    @Nested
    @DisplayName("Недопустимые пороги")
    class InvalidThresholds {
        @Test
        @DisplayName("Бросает исключение при недопустимом положительном пороге (меньше 0 или больше 1)")
        void testInvalidPositiveThresholdThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> new UpsideSignalAmplifier(delegate, -0.1, 0.5));
            assertThrows(IllegalArgumentException.class, () -> new UpsideSignalAmplifier(delegate, 1.1, 0.5));
        }

        @Test
        @DisplayName("Бросает исключение при недопустимом отрицательном пороге (меньше 0 или больше 1)")
        void testInvalidNegativeThresholdThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> new UpsideSignalAmplifier(delegate, 0.5, -0.1));
            assertThrows(IllegalArgumentException.class, () -> new UpsideSignalAmplifier(delegate, 0.5, 1.1));
        }
    }

    // === Amplification behavior tests ===

    @Nested
    @DisplayName("Положительный сигнал")
    class PositiveSignal {
        @Test
        @DisplayName("Выше положительного порога → возвращает +1.0")
        void testAbovePositiveThresholdReturnsOne() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(0.8, 0.8));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.5, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(1.0, result.signal());
            assertEquals(0.8, result.strength());
        }

        @Test
        @DisplayName("Ниже положительного порога → возвращает исходный сигнал")
        void testBelowPositiveThresholdReturnsOriginalSignal() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(0.3, 0.3));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.5, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(0.3, result.signal());
            assertEquals(0.3, result.strength());
        }

        @Test
        @DisplayName("На положительном пороге → возвращает исходный сигнал")
        void testAtPositiveThresholdReturnsOriginalSignal() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(0.5, 0.5));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.5, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(0.5, result.signal());
        }
    }

    @Nested
    @DisplayName("Отрицательный сигнал")
    class NegativeSignal {
        @Test
        @DisplayName("Ниже отрицательного порога → возвращает -1.0")
        void testBelowNegativeThresholdReturnsMinusOne() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(-0.8, -0.8));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.5, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(-1.0, result.signal());
            assertEquals(-0.8, result.strength());
        }

        @Test
        @DisplayName("Выше отрицательного порога → возвращает исходный сигнал")
        void testAboveNegativeThresholdReturnsOriginalSignal() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(-0.3, -0.3));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.5, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(-0.3, result.signal());
            assertEquals(-0.3, result.strength());
        }

        @Test
        @DisplayName("На отрицательном пороге → возвращает исходный сигнал")
        void testAtNegativeThresholdReturnsOriginalSignal() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(-0.5, -0.5));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.5, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(-0.5, result.signal());
        }
    }

    @Nested
    @DisplayName("Пороги по умолчанию (0.5)")
    class DefaultThresholds {
        @Test
        @DisplayName("Сигнал 0.6 выше порога → усиливается до +1.0")
        void testDefaultThresholdIsZeroPointFive() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(0.6, 0.6));

            var amplifier = new UpsideSignalAmplifier(delegate);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(1.0, result.signal());
        }

        @Test
        @DisplayName("Нейтральный сигнал → возвращается как есть")
        void testDelegateReturnsNeutralReturnsOriginalSignal() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(Upside.NEUTRAL);

            var amplifier = new UpsideSignalAmplifier(delegate);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(0.0, result.signal());
        }
    }

    @Nested
    @DisplayName("Разные пороги для зон")
    class DifferentThresholds {
        @Test
        @DisplayName("Положительный сигнал с разными порогами (0.6, 0.4)")
        void testPositiveSignalWithDifferentThresholds() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(0.7, 0.7));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.6, 0.4);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(1.0, result.signal());
        }

        @Test
        @DisplayName("Отрицательный сигнал с разными порогами (0.4, 0.5)")
        void testNegativeSignalWithDifferentThresholds() {
            when(delegate.calculate(EMPTY_CANDLES)).thenReturn(new Upside(-0.6, -0.6));

            var amplifier = new UpsideSignalAmplifier(delegate, 0.4, 0.5);
            var result = amplifier.calculate(EMPTY_CANDLES);

            assertEquals(-1.0, result.signal());
        }
    }
}
