package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.LevelPairSelector;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyLevelsUpsideCalculatorTest {

    // Простая заглушка Candle
    private Candle candle(double close) {
        return Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, close);
    }

    // Простая заглушка Level
    private Level<Double> level(double value, double strength) {
        return new Level<>(0, 100, idx -> value, strength);
    }

    // Простая заглушка LevelPair
    private LevelPair levelPair(double resValue, double supValue, double resStrength, double supStrength) {
        return new LevelPair(level(resValue, resStrength), level(supValue, supStrength));
    }

    // Простая заглушка Upside
    private Upside upside(double signal, double strength) {
        return new Upside(signal, strength);
    }

    @Nested
    @DisplayName("Базовый расчёт")
    class BasicCalculation {

        @Test
        @DisplayName("Должен вернуть NEUTRAL при пустом списке свечей")
        void shouldReturnNeutralOnEmptyCandles() {
            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mock(LevelDetector.class),
                mock(LevelDetector.class),
                mock(LevelBasedUpsideCalculator.class),
                mock(LevelPairSelector.class),
                mock(UpsideCalculator.class)
            );

            Upside result = calc.calculate(Collections.emptyList());
            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при null свечах")
        void shouldReturnNeutralOnNullCandles() {
            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mock(LevelDetector.class),
                mock(LevelDetector.class),
                mock(LevelBasedUpsideCalculator.class),
                mock(LevelPairSelector.class),
                mock(UpsideCalculator.class)
            );

            Upside result = calc.calculate(null);
            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Поведение при отсутствии уровней")
    class FallbackBehavior {

        @Test
        @DisplayName("Должен использовать fallback при пустых уровнях")
        void shouldUseFallbackWhenNoLevels() {
            LevelDetector mockSupport = mock(LevelDetector.class);
            when(mockSupport.detect(any())).thenReturn(Collections.emptyList());

            LevelDetector mockResistance = mock(LevelDetector.class);
            when(mockResistance.detect(any())).thenReturn(Collections.emptyList());

            UpsideCalculator mockFallback = mock(UpsideCalculator.class);
            when(mockFallback.calculate(any())).thenReturn(upside(0.6, 0.8));

            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mockSupport,
                mockResistance,
                mock(LevelBasedUpsideCalculator.class),
                mock(LevelPairSelector.class),
                mockFallback
            );

            List<Candle> candles = List.of(candle(100.0));
            Upside result = calc.calculate(candles);

            assertEquals(0.6, result.signal(), 0.001);
            assertEquals(0.8, result.strength(), 0.001);
            verify(mockFallback).calculate(candles);
        }
    }

    @Nested
    @DisplayName("Один уровень")
    class SingleLevel {

        @Test
        @DisplayName("Должен вернуть результат от levelBasedCalculator при одном уровне")
        void shouldUseLevelBasedCalculatorForSingleLevel() {
            LevelDetector mockSupport = mock(LevelDetector.class);
            when(mockSupport.detect(any())).thenReturn(List.of(level(95.0, 3.0)));

            LevelDetector mockResistance = mock(LevelDetector.class);
            when(mockResistance.detect(any())).thenReturn(List.of(level(105.0, 4.0)));

            LevelBasedUpsideCalculator mockLevelBased = mock(LevelBasedUpsideCalculator.class);
            when(mockLevelBased.calculate(any(), any())).thenReturn(upside(0.7, 0.9));

            LevelPairSelector mockSelector = mock(LevelPairSelector.class);
            when(mockSelector.select(any(), any(), any()))
                .thenReturn(List.of(levelPair(105.0, 95.0, 4.0, 3.0)));

            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mockSupport,
                mockResistance,
                mockLevelBased,
                mockSelector,
                mock(UpsideCalculator.class)
            );

            List<Candle> candles = List.of(candle(100.0));
            Upside result = calc.calculate(candles);

            assertEquals(0.7, result.signal(), 0.001);
            assertEquals(0.9, result.strength(), 0.001);
            verify(mockLevelBased).calculate(any(LevelPair.class), eq(candles));
        }
    }

    @Nested
    @DisplayName("Несколько уровней")
    class MultipleLevels {

        @Test
        @DisplayName("Должен объединить уровни по взвешенному среднему")
        void shouldCombineMultipleLevelsByWeightedAverage() {
            LevelDetector mockSupport = mock(LevelDetector.class);
            when(mockSupport.detect(any())).thenReturn(List.of(level(95.0, 3.0)));

            LevelDetector mockResistance = mock(LevelDetector.class);
            when(mockResistance.detect(any())).thenReturn(List.of(level(105.0, 4.0)));

            LevelBasedUpsideCalculator mockLevelBased = mock(LevelBasedUpsideCalculator.class);
            LevelPair levelPair1 = levelPair(105.0, 95.0, 4.0, 3.0);
            LevelPair levelPair2 = levelPair(110.0, 90.0, 2.0, 1.0);
            when(mockLevelBased.calculate(eq(levelPair1), any()))
                .thenReturn(upside(0.6, 0.7));
            when(mockLevelBased.calculate(eq(levelPair2), any()))
                .thenReturn(upside(0.8, 0.9));

            LevelPairSelector mockSelector = mock(LevelPairSelector.class);
            when(mockSelector.select(any(), any(), any())).thenReturn(List.of(levelPair1, levelPair2));

            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mockSupport,
                mockResistance,
                mockLevelBased,
                mockSelector,
                mock(UpsideCalculator.class)
            );

            List<Candle> candles = List.of(candle(100.0));
            Upside result = calc.calculate(candles);

            // weight1 = 4+3=7, weight2 = 2+1=3 → total=10
            // signal = (0.6*7 + 0.8*3)/10 = (4.2+2.4)/10 = 0.66
            // strength = (0.7*7 + 0.9*3)/10 = (4.9+2.7)/10 = 0.76
            assertEquals(0.66, result.signal(), 0.001);
            assertEquals(0.76, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При нулевом суммарном весе — возвращает NEUTRAL")
        void shouldReturnNeutralWhenTotalWeightIsZero() {
            LevelDetector mockSupport = mock(LevelDetector.class);
            when(mockSupport.detect(any())).thenReturn(List.of(level(95.0, 0.0)));

            LevelDetector mockResistance = mock(LevelDetector.class);
            when(mockResistance.detect(any())).thenReturn(List.of(level(105.0, 0.0)));

            LevelBasedUpsideCalculator mockLevelBased = mock(LevelBasedUpsideCalculator.class);
            when(mockLevelBased.calculate(any(), any())).thenReturn(upside(0.5, 0.6));

            LevelPairSelector mockSelector = mock(LevelPairSelector.class);
            when(mockSelector.select(any(), any(), any()))
                .thenReturn(
                    List.of(
                        levelPair(105.0, 95.0, 0.0, 0.0),
                        levelPair(110.0, 90.0, 0.0, 0.0)
                    )
                );

            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mockSupport,
                mockResistance,
                mockLevelBased,
                mockSelector,
                mock(UpsideCalculator.class)
            );

            List<Candle> candles = List.of(candle(100.0));
            Upside result = calc.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При пустом списке уровней — fallback вызывается")
        void shouldCallFallbackOnEmptyLevels() {
            LevelDetector mockSupport = mock(LevelDetector.class);
            when(mockSupport.detect(any())).thenReturn(Collections.emptyList());

            LevelDetector mockResistance = mock(LevelDetector.class);
            when(mockResistance.detect(any())).thenReturn(Collections.emptyList());

            UpsideCalculator mockFallback = mock(UpsideCalculator.class);
            when(mockFallback.calculate(any())).thenReturn(upside(0.4, 0.5));

            KeyLevelsUpsideCalculator calc = new KeyLevelsUpsideCalculator(
                mockSupport,
                mockResistance,
                mock(LevelBasedUpsideCalculator.class),
                mock(LevelPairSelector.class),
                mockFallback
            );

            List<Candle> candles = List.of(candle(100.0));
            Upside result = calc.calculate(candles);

            assertEquals(0.4, result.signal(), 0.001);
            assertEquals(0.5, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При null в levelBasedCalculator — исключение")
        void shouldThrowOnNullLevelBasedCalculator() {
            assertThrows(NullPointerException.class, () -> new KeyLevelsUpsideCalculator(
                mock(LevelDetector.class),
                mock(LevelDetector.class),
                null,
                mock(LevelPairSelector.class),
                mock(UpsideCalculator.class)
            ));
        }
    }
}
