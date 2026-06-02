package com.siberalt.singularity.strategy.upside.level.proximity;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultPriceProximityFunctionTest {

    // Простая заглушка Candle
    private Candle candle(double close, double high, double low) {
        return Candle.of(TimePoint.NULL, 0, 0, high, low, close);
    }

    // Простая заглушка Level
    private Level<Double> level(double value) {
        return new Level<>(10, 100, idx -> value);
    }

    @Nested
    @DisplayName("Базовый расчёт близости")
    class BasicCalculation {

        @Test
        @DisplayName("Должен вернуть 1.0 при совпадении цен")
        void shouldReturnOneWhenPricesMatch() {
            VolatilityCalculator mockVolatility = mock(VolatilityCalculator.class);
            when(mockVolatility.calculate(any())).thenReturn(1.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, mockVolatility
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(100.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            assertEquals(1.0, proximity, 0.001);
        }

        @Test
        @DisplayName("Должен уменьшаться с ростом расстояния")
        void shouldDecreaseWithDistance() {
            VolatilityCalculator mockVolatility = mock(VolatilityCalculator.class);
            when(mockVolatility.calculate(any())).thenReturn(1.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, mockVolatility
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            List<Candle> history = List.of(candle);

            double closeProximity = func.compute(candle, level(100.1), history);
            double farProximity = func.compute(candle, level(105.0), history);

            assertTrue(closeProximity > farProximity);
            assertTrue(farProximity < 1.0);
        }
    }

    @Nested
    @DisplayName("Влияние волатильности")
    class VolatilityInfluence {

        @Test
        @DisplayName("При высокой волатильности — близость выше (меньше штраф за расстояние)")
        void highVolatilityShouldIncreaseProximity() {
            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(102.0);
            List<Candle> history = List.of(candle);

            VolatilityCalculator lowVol = mock(VolatilityCalculator.class);
            when(lowVol.calculate(any())).thenReturn(0.5); // низкая волатильность

            VolatilityCalculator highVol = mock(VolatilityCalculator.class);
            when(highVol.calculate(any())).thenReturn(5.0); // высокая волатильность

            DefaultPriceProximityFunction lowVolFunc = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, lowVol
            );
            DefaultPriceProximityFunction highVolFunc = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, highVol
            );

            double lowProximity = lowVolFunc.compute(candle, level, history);
            double highProximity = highVolFunc.compute(candle, level, history);

            assertTrue(highProximity > lowProximity);
        }

        @Test
        @DisplayName("При нулевой волатильности — использует fallback с относительным расстоянием")
        void shouldUseFallbackWhenVolatilityIsZero() {
            VolatilityCalculator zeroVol = mock(VolatilityCalculator.class);
            when(zeroVol.calculate(any())).thenReturn(0.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, zeroVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(101.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            // Расстояние = 1.0, цена = 100 → относительное = 0.01
            // exp(-10 * 0.01 / 0.001) = exp(-100) ≈ 0
            assertTrue(proximity > 0);
            assertTrue(proximity < 1.0);
        }
    }

    @Nested
    @DisplayName("Кастомный PriceExtractor")
    class CustomPriceExtractor {

        @Test
        @DisplayName("Поддерживает извлечение цены по максимуму")
        void shouldWorkWithHighPriceExtractor() {
            VolatilityCalculator mockVol = mock(VolatilityCalculator.class);
            when(mockVol.calculate(any())).thenReturn(1.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getHighAsDouble, 10.0, 0.5, mockVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(105.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            assertEquals(1.0, proximity, 0.001);
        }

        @Test
        @DisplayName("Поддерживает извлечение цены по минимуму")
        void shouldWorkWithLowPriceExtractor() {
            VolatilityCalculator mockVol = mock(VolatilityCalculator.class);
            when(mockVol.calculate(any())).thenReturn(1.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getLowAsDouble, 10.0, 0.5, mockVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(95.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            assertEquals(1.0, proximity, 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null списке свечей — волатильность = 0 → fallback")
        void shouldUseFallbackOnNullCandles() {
            VolatilityCalculator mockVol = mock(VolatilityCalculator.class);
            when(mockVol.calculate(null)).thenReturn(0.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, mockVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(101.0);

            double proximity = func.compute(candle, level, null);

            assertTrue(proximity >= 0.0);
            assertTrue(proximity <= 1.0);
        }

        @Test
        @DisplayName("При пустом списке свечей — волатильность = 0 → fallback")
        void shouldUseFallbackOnEmptyCandles() {
            VolatilityCalculator mockVol = mock(VolatilityCalculator.class);
            when(mockVol.calculate(Collections.emptyList())).thenReturn(0.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, mockVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(101.0);

            double proximity = func.compute(candle, level, Collections.emptyList());

            assertTrue(proximity >= 0.0);
            assertTrue(proximity <= 1.0);
        }

        @Test
        @DisplayName("При levelPrice = 0 — не должно быть деления на 0")
        void shouldHandleZeroLevelPrice() {
            VolatilityCalculator mockVol = mock(VolatilityCalculator.class);
            when(mockVol.calculate(any())).thenReturn(1.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.5, mockVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(0.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            assertTrue(proximity >= 0.0);
            assertTrue(proximity <= 1.0);
        }

        @Test
        @DisplayName("При multiplier = 0 — не должно быть деления на 0")
        void shouldHandleZeroMultiplier() {
            VolatilityCalculator mockVol = mock(VolatilityCalculator.class);
            when(mockVol.calculate(any())).thenReturn(1.0);

            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction(
                Candle::getCloseAsDouble, 10.0, 0.0, mockVol
            );

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(101.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            // normalizedDistance = 1.0 / (1.0 * 0.0) → Infinity → exp(-10 * Inf) = 0
            assertEquals(0.0, proximity, 0.001);
        }
    }

    @Nested
    @DisplayName("Конструктор по умолчанию")
    class DefaultConstructor {

        @Test
        @DisplayName("Конструктор по умолчанию должен работать")
        void shouldInitializeWithDefaults() {
            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction();

            assertNotNull(func.priceExtractor());
            assertEquals(10.0, func.decayFactor(), 0.001);
            assertEquals(0.5, func.multiplier(), 0.001);
            assertNotNull(func.volatilityCalculator());
            assertInstanceOf(ATRVolatilityCalculator.class, func.volatilityCalculator());
        }

        @Test
        @DisplayName("Конструктор по умолчанию — корректно вычисляет")
        void shouldComputeWithDefaultSettings() {
            DefaultPriceProximityFunction func = new DefaultPriceProximityFunction();

            Candle candle = candle(100.0, 105.0, 95.0);
            Level<Double> level = level(100.0);
            List<Candle> history = List.of(candle);

            double proximity = func.compute(candle, level, history);

            assertEquals(1.0, proximity, 0.001);
        }
    }
}
