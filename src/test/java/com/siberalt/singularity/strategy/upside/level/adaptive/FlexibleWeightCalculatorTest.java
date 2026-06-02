package com.siberalt.singularity.strategy.upside.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.level.proximity.PriceProximityFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlexibleWeightCalculatorTest {

    // Простая заглушка Candle
    private Candle candle(double close) {
        return Candle.of(Instant.parse("2024-01-01T00:00:00Z"), 0, close);
    }

    // Простая заглушка Level
    private Level<Double> level(double value) {
        return new Level<>(10L, 20L, idx -> value);
    }

    // Простая заглушка LevelPair
    private LevelPair levelPair(double resValue, double supValue) {
        return new LevelPair(level(resValue), level(supValue));
    }

    // Простая заглушка Upside
    private Upside upside(double signal) {
        return new Upside(signal, 0);
    }

    @Nested
    @DisplayName("Базовый расчёт весов")
    class BasicCalculation {

        @Test
        @DisplayName("Должен корректно рассчитать веса при нулевых бонусах")
        void shouldCalculateBaseWeights() {
            PriceProximityFunction mockProximity = (candle, level, candles) -> 0.0;

            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.2, 0.8, mockProximity
            );

            Upside levels = upside(0.6);
            Upside volume = upside(0.8);
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(105.0, 95.0);

            WeightFactors weights = calc.compute(levels, volume, candles, pair);

            // base = 0.4, absSignal = 0.8 → 0.4 + 0.5*0.8 = 0.4 + 0.4 = 0.8
            // bonuses = 0 → volumeWeight = 0.8 → levelsWeight = 0.2 → нормализация: 0.8+0.2=1.0
            assertEquals(0.2, weights.levelsWeight(), 0.001);
            assertEquals(0.8, weights.volumeWeight(), 0.001);
        }
    }

    @Nested
    @DisplayName("Влияние близости к уровню")
    class ProximityInfluence {

        @Test
        @DisplayName("Близость к уровню должна увеличивать вес объёмов")
        void proximityShouldIncreaseVolumeWeight() {
            PriceProximityFunction mockProximity = (candle, level, candles) -> 1.0;

            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.2, 0.8, mockProximity
            );

            Upside levels = upside(0.6);
            Upside volume = upside(0.5);
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(100.0, 100.0); // свеча на уровне

            WeightFactors weights = calc.compute(levels, volume, candles, pair);

            // base = 0.4, absSignal = 0.5 → 0.4 + 0.25 = 0.65
            // bonus = 0.15 * 1.0 = 0.15 → total = 0.8 → clamp → 0.8
            // levels = 0.2 → нормализация: 0.8+0.2=1.0
            assertEquals(0.2, weights.levelsWeight(), 0.001);
            assertEquals(0.8, weights.volumeWeight(), 0.001);
        }
    }

    @Nested
    @DisplayName("Влияние дивергенции")
    class DivergenceInfluence {

        @Test
        @DisplayName("Дивергенция (разные знаки) должна увеличивать вес объёмов")
        void divergenceShouldIncreaseVolumeWeight() {
            PriceProximityFunction mockProximity = (candle, level, candles) -> 0.0;

            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.2, 0.8, mockProximity
            );

            Upside levels = upside(0.6);
            Upside volume = upside(-0.7); // дивергенция
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(105.0, 95.0);

            WeightFactors weights = calc.compute(levels, volume, candles, pair);

            // base = 0.4, absSignal = 0.7 → 0.4 + 0.35 = 0.75
            // divergence = 0.2 * (0.6 * 0.7) = 0.2 * 0.42 = 0.084 → total = 0.834 → clamp → 0.8
            // levels = 0.2 → нормализация
            assertEquals(0.2, weights.levelsWeight(), 0.001);
            assertEquals(0.8, weights.volumeWeight(), 0.001);
        }

        @Test
        @DisplayName("Нет дивергенции — бонус не применяется")
        void noDivergenceShouldNotAddBonus() {
            PriceProximityFunction mockProximity = (candle, level, candles) -> 0.0;

            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.2, 0.8, mockProximity
            );

            Upside levels = upside(0.6);
            Upside volume = upside(0.7); // одинаковый знак
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(105.0, 95.0);

            WeightFactors weights = calc.compute(levels, volume, candles, pair);

            // base = 0.4, absSignal = 0.7 → 0.4 + 0.35 = 0.75 → clamp → 0.75
            // levels = 0.25 → нормализация
            assertEquals(0.25, weights.levelsWeight(), 0.001);
            assertEquals(0.75, weights.volumeWeight(), 0.001);
        }
    }

    @Nested
    @DisplayName("Ограничения и нормализация")
    class ClampingAndNormalization {

        @Test
        @DisplayName("Веса должны быть ограничены min и max")
        void weightsShouldBeClamped() {
            PriceProximityFunction mockProximity = (candle, level, candles) -> 1.0;

            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 10.0, 10.0, 10.0, 0.2, 0.8, mockProximity
            );

            Upside levels = upside(1.0);
            Upside volume = upside(1.0);
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(100.0, 100.0);

            WeightFactors weights = calc.compute(levels, volume, candles, pair);

            // Даже при огромных коэффициентах — вес объёмов ограничен 0.8
            assertEquals(0.2, weights.levelsWeight(), 0.001);
            assertEquals(0.8, weights.volumeWeight(), 0.001);
        }

        @Test
        @DisplayName("Нормализация должна работать при выходе за пределы")
        void normalizationShouldWorkAfterClamping() {
            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.3, 0.7, mock(PriceProximityFunction.class)
            );

            // Подменим поведение
            PriceProximityFunction mockProximity = (candle, level, candles) -> 1.0;
            FlexibleWeightCalculator spyCalc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.3, 0.7, mockProximity
            );

            Upside levels = upside(0.6);
            Upside volume = upside(0.8);
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(100.0, 100.0);

            WeightFactors weights = spyCalc.compute(levels, volume, candles, pair);

            // volumeWeight = 0.4 + 0.4 + 0.15 + 0.2*0 = 0.95 → clamp → 0.7
            // levelsWeight = 1 - 0.7 = 0.3 → clamp → 0.3 → total = 1.0 → нормализация не меняет
            assertEquals(0.3, weights.levelsWeight(), 0.001);
            assertEquals(0.7, weights.volumeWeight(), 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При пустом списке свечей — возвращает 0.5/0.5")
        void shouldReturnEqualWeightsOnEmptyCandles() {
            FlexibleWeightCalculator calc = new FlexibleWeightCalculator();
            WeightFactors weights = calc.compute(
                upside(1.0), upside(1.0), Collections.emptyList(), levelPair(100, 90)
            );
            assertEquals(0.5, weights.levelsWeight(), 0.001);
            assertEquals(0.5, weights.volumeWeight(), 0.001);
        }

        @Test
        @DisplayName("При null свечах — возвращает 0.5/0.5")
        void shouldReturnEqualWeightsOnNullCandles() {
            FlexibleWeightCalculator calc = new FlexibleWeightCalculator();
            WeightFactors weights = calc.compute(
                upside(1.0), upside(1.0), null, levelPair(100, 90)
            );
            assertEquals(0.5, weights.levelsWeight(), 0.001);
            assertEquals(0.5, weights.volumeWeight(), 0.001);
        }

        @Test
        @DisplayName("При abs(signal) > 1 — всё равно работает")
        void shouldHandleSignalGreaterThanOne() {
            PriceProximityFunction mockProximity = (candle, level, candles) -> 0.0;
            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(0.4, 0.5, 0.15, 0.2, 0.2, 0.8, mockProximity);

            Upside levels = upside(1.5);
            Upside volume = upside(2.0);
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(105.0, 95.0);

            WeightFactors weights = calc.compute(levels, volume, candles, pair);

            // absSignal = 2.0 → 0.4 + 0.5*2.0 = 1.4 → clamp → 0.8
            assertEquals(0.2, weights.levelsWeight(), 0.001);
            assertEquals(0.8, weights.volumeWeight(), 0.001);
        }

        @Test
        @DisplayName("При сумме весов <= 0 — возвращает 0.5/0.5")
        void shouldReturnDefaultOnInvalidTotal() {
            FlexibleWeightCalculator calc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.0, 0.0, mock(PriceProximityFunction.class)
            );

            // Подменим поведение
            PriceProximityFunction mockProximity = (candle, level, candles) -> 0.0;
            FlexibleWeightCalculator spyCalc = new FlexibleWeightCalculator(
                0.4, 0.5, 0.15, 0.2, 0.0, 0.0, mockProximity
            );

            Upside levels = upside(0.5);
            Upside volume = upside(0.5);
            List<Candle> candles = List.of(candle(100.0));
            LevelPair pair = levelPair(105.0, 95.0);

            WeightFactors weights = spyCalc.compute(levels, volume, candles, pair);

            assertEquals(0.5, weights.levelsWeight(), 0.001);
            assertEquals(0.5, weights.volumeWeight(), 0.001);
        }
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {

        @Test
        @DisplayName("Конструктор по умолчанию должен работать")
        void shouldWorkWithDefaultConstructor() {
            FlexibleWeightCalculator calc = new FlexibleWeightCalculator();

            assertNotNull(calc);
            // Проверим, что можно вызвать
            WeightFactors weights = calc.compute(
                upside(0.5), upside(0.5), List.of(candle(100.0)), levelPair(105.0, 95.0)
            );
            assertTrue(weights.levelsWeight() > 0);
            assertTrue(weights.volumeWeight() > 0);
        }
    }
}
