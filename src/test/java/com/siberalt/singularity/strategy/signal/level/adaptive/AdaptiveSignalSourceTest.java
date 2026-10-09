package com.siberalt.singularity.strategy.signal.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;
import com.siberalt.singularity.strategy.signal.level.LevelBasedSignalSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdaptiveSignalSourceTest {

    // Простая заглушка Candle
    private Candle candle(double close) {
        return Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, close);
    }

    // Простая заглушка LevelPair
    private LevelPair levelPair() {
        return new LevelPair(
            new Level<>(0, 100, x -> 200.0),
            new Level<>(0, 100, x -> 100.0)
        );
    }

    // Простая заглушка Signal
    private Signal signal(double signal, double strength) {
        return new Signal(signal, strength);
    }

    @Nested
    @DisplayName("Базовое взвешивание")
    class BasicWeighting {

        @Test
        @DisplayName("Должен корректно объединить сигналы по весам")
        void shouldCombineSignalsByWeights() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.6, 0.8));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(any())).thenReturn(signal(0.4, 0.7));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), any(), any()))
                .thenReturn(new WeightFactors(0.3, 0.7));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            LevelPair pair = levelPair();
            List<Candle> candles = List.of(candle(100.0));

            Signal result = calc.calculate(pair, candles);

            // signal = 0.3*0.6 + 0.7*0.4 = 0.18 + 0.28 = 0.46
            // strength = 0.3*0.8 + 0.7*0.7 = 0.24 + 0.49 = 0.73
            assertEquals(0.46, result.confidence(), 0.001);
            assertEquals(0.73, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При весе levels=1.0 — возвращает только levelsSignal")
        void shouldReturnOnlyLevelsWhenVolumeWeightIsZero() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.8, 0.9));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(any())).thenReturn(signal(0.2, 0.3));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), any(), any()))
                .thenReturn(new WeightFactors(1.0, 0.0));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            Signal result = calc.calculate(levelPair(), List.of(candle(100.0)));

            assertEquals(0.8, result.confidence(), 0.001);
            assertEquals(0.9, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При весе volume=1.0 — возвращает только volumeSignal")
        void shouldReturnOnlyVolumeWhenLevelsWeightIsZero() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.3, 0.4));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(any())).thenReturn(signal(0.9, 0.8));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), any(), any()))
                .thenReturn(new WeightFactors(0.0, 1.0));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            Signal result = calc.calculate(levelPair(), List.of(candle(100.0)));

            assertEquals(0.9, result.confidence(), 0.001);
            assertEquals(0.8, result.strength(), 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null свечах — возвращает NEUTRAL")
        void shouldReturnNeutralOnNullCandles() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.5, 0.6));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(null)).thenReturn(signal(0.5, 0.6));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), eq(null), any()))
                .thenReturn(new WeightFactors(0.5, 0.5));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            Signal result = calc.calculate(levelPair(), null);

            assertEquals(0.5, result.confidence(), 0.001);
            assertEquals(0.6, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При пустом списке свечей — работает корректно")
        void shouldHandleEmptyCandles() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.5, 0.6));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(Collections.emptyList())).thenReturn(signal(0.5, 0.6));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), any(), any()))
                .thenReturn(new WeightFactors(0.5, 0.5));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            Signal result = calc.calculate(levelPair(), Collections.emptyList());

            assertEquals(0.5, result.confidence(), 0.001);
            assertEquals(0.6, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При нулевых весах — возвращает NEUTRAL")
        void shouldReturnNeutralWhenBothWeightsAreZero() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.5, 0.6));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(any())).thenReturn(signal(0.5, 0.6));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), any(), any()))
                .thenReturn(new WeightFactors(0.0, 0.0));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            Signal result = calc.calculate(levelPair(), List.of(candle(100.0)));

            assertEquals(0.0, result.confidence(), 0.001);
            assertEquals(0.0, result.strength(), 0.001);
        }

        @Test
        @DisplayName("При NaN в сигнале — возвращает NaN")
        void shouldPropagateNaN() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(Double.NaN, 0.6));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(any())).thenReturn(signal(0.5, 0.6));

            WeightCalculator mockWeight = mock(WeightCalculator.class);
            when(mockWeight.compute(any(), any(), any(), any()))
                .thenReturn(new WeightFactors(0.5, 0.5));

            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, mockWeight
            );

            Signal result = calc.calculate(levelPair(), List.of(candle(100.0)));

            assertTrue(Double.isNaN(result.confidence()));
        }
    }

    @Nested
    @DisplayName("Интеграция с FlexibleWeightCalculator")
    class IntegrationWithFlexibleWeightCalculator {

        @Test
        @DisplayName("Должен корректно работать с реальным FlexibleWeightCalculator")
        void shouldWorkWithRealFlexibleWeightCalculator() {
            LevelBasedSignalSource mockLevels = mock(LevelBasedSignalSource.class);
            when(mockLevels.calculate(any(), any())).thenReturn(signal(0.6, 0.8));

            SignalSource mockVolume = mock(SignalSource.class);
            when(mockVolume.calculate(any())).thenReturn(signal(0.8, 0.9));

            FlexibleWeightCalculator weightCalc = new FlexibleWeightCalculator();
            AdaptiveSignalSource calc = new AdaptiveSignalSource(
                mockLevels, mockVolume, weightCalc
            );

            LevelPair pair = levelPair();
            List<Candle> candles = List.of(candle(100.0));

            Signal result = calc.calculate(pair, candles);

            // weightCalc вернёт что-то вроде (0.2, 0.8)
            assertTrue(result.confidence() >= 0.6);
            assertTrue(result.strength() >= 0.8);
        }
    }
}
