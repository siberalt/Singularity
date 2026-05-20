package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class SimpleStrengthCalculatorTest {

    // Простая заглушка Candle
    private Candle candle(long index, double price) {
        return Candle.of(new TimePoint(index),  price);
    }

    // Простая заглушка Level
    private Level<Double> level(long from, long to, int touches, double levelValueAt) {
        return new Level<>(new TimePoint(from), new TimePoint(to), idx -> levelValueAt, 0, touches);
    }

    @Nested
    @DisplayName("Базовая сила: касания × log(длительность)")
    class BaseStrength {

        @Test
        @DisplayName("Должен корректно рассчитать базовую силу")
        void shouldCalculateBaseStrengthCorrectly() {
            Level<Double> level = level(10, 20, 3, 100.0); // duration = 11
            List<Candle> candles = List.of(candle(25, 100.0));

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator(10.0, 30.0);
            double strength = calc.calculate(level, candles);

            double durationWeight = Math.log(11 + 1); // ln(12)
            double expectedBase = 3 * durationWeight;

            // priceFactor = 1.0 (если цена совпадает), timeFactor = 1.0 (если касание свежее)
            // но у нас barsSinceLastTouch = 25 - 20 = 5 → timeFactor < 1
            double timeFactor = Math.exp(-5.0 / 30.0);
            double expected = expectedBase * 1.0 * timeFactor;

            assertEquals(expected, strength, 0.001);
        }

        @Test
        @DisplayName("При нулевой длительности — возвращает только touchesCount")
        void shouldReturnTouchesCountWhenDurationIsZero() {
            Level<Double> level = level(10, 10, 5, 100.0); // duration = 1 → log(2)
            List<Candle> candles = List.of(candle(15, 100.0));

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator();
            double strength = calc.calculate(level, candles);

            double durationWeight = Math.log(1 + 1); // ln(2)
            double timeFactor = Math.exp(-5.0 / 30.0);
            double expected = 5 * durationWeight * timeFactor;

            assertEquals(expected, strength, 0.001);
        }
    }

    @Nested
    @DisplayName("Ценовая близость")
    class PriceProximity {

        @Test
        @DisplayName("Чем ближе цена к уровню — тем выше priceFactor")
        void priceFactorShouldDecreaseWithDistance() {
            Level<Double> level = level(10, 20, 1, 100.0);
            List<Candle> candles = List.of(candle(25, 100.0)); // distance = 0

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator(10.0, 30.0);
            double strength = calc.calculate(level, candles);

            double durationWeight = Math.log(11 + 1);
            double timeFactor = Math.exp(-5.0 / 30.0);
            double expected = 1 * durationWeight * 1.0 * timeFactor; // priceFactor = 1.0

            assertEquals(expected, strength, 0.001);
        }

        @Test
        @DisplayName("При большой ценовой дистанции — priceFactor стремится к 0")
        void priceFactorShouldApproachZeroAtLargeDistance() {
            Level<Double> level = level(10, 20, 1, 100.0);
            List<Candle> candles = List.of(candle(25, 150.0)); // distance = 50 / 150 ≈ 0.333

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator(10.0, 30.0);
            double strength = calc.calculate(level, candles);

            double distance = Math.abs(100.0 - 150.0) / 150.0;
            double priceFactor = Math.exp(-distance * 10.0); // ≈ exp(-3.33) ≈ 0.035

            assertTrue(strength > 0);
            assertTrue(strength < 1); // сильно ослаблен
        }
    }

    @Nested
    @DisplayName("Временная давность")
    class TimeDecay {

        @Test
        @DisplayName("Чем свежее касание — тем выше timeFactor")
        void timeFactorShouldBeHigherForRecentTouches() {
            Level<Double> recent = level(20, 24, 1, 100.0); // last touch at 24
            Level<Double> old = level(10, 14, 1, 100.0);    // last touch at 14
            List<Candle> candles = List.of(candle(25, 100.0));

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator(0.0, 30.0); // priceSensitivity = 0 → priceFactor = 1

            double strengthRecent = calc.calculate(recent, candles);
            double strengthOld = calc.calculate(old, candles);

            double timeFactorRecent = Math.exp(-1.0 / 30.0); // 25 - 24 = 1
            double timeFactorOld = Math.exp(-11.0 / 30.0);   // 25 - 14 = 11

            assertTrue(strengthRecent > strengthOld);
        }

        @Test
        @DisplayName("При очень старом касании — timeFactor ≈ 0")
        void timeFactorShouldApproachZeroForVeryOldTouches() {
            Level<Double> level = level(10, 10, 1, 100.0);
            List<Candle> candles = List.of(candle(1000, 100.0)); // barsSinceLastTouch = 990

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator(0.0, 30.0);
            double strength = calc.calculate(level, candles);

            double timeFactor = Math.exp(-990.0 / 30.0); // ≈ exp(-33) ≈ 0
            assertEquals(0.0, strength, 1e-10);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null списке свечей — возвращает baseStrength без timeFactor")
        void shouldHandleNullCandles() {
            Level<Double> level = level(10, 20, 2, 100.0);
            SimpleStrengthCalculator calc = new SimpleStrengthCalculator();

            double strength = calc.calculate(level, null);

            double durationWeight = Math.log(11 + 1);
            double expected = 2 * durationWeight; // priceFactor = 1, timeFactor = 1 (no decay)
            assertEquals(expected, strength, 0.001);
        }

        @Test
        @DisplayName("При пустом списке свечей — возвращает baseStrength")
        void shouldHandleEmptyCandles() {
            Level<Double> level = level(10, 20, 3, 100.0);
            SimpleStrengthCalculator calc = new SimpleStrengthCalculator();

            double strength = calc.calculate(level, Collections.emptyList());

            double durationWeight = Math.log(11 + 1);
            double expected = 3 * durationWeight;
            assertEquals(expected, strength, 0.001);
        }

        @Test
        @DisplayName("При null уровне — выбрасывает исключение")
        void shouldThrowOnNullLevel() {
            SimpleStrengthCalculator calc = new SimpleStrengthCalculator();
            assertThrows(NullPointerException.class, () -> calc.calculate(null, List.of()));
        }
    }

    @Nested
    @DisplayName("Кастомный PriceExtractor")
    class CustomPriceExtractor {

        @Test
        @DisplayName("Поддерживает кастомный извлечения цены")
        void shouldWorkWithCustomPriceExtractor() {
            Level<Double> level = level(10, 20, 1, 100.0);
            List<Candle> candles = List.of(candle(25, 105.0));

            // Извлекаем цену как (close + open)/2, но у нас нет open → просто +5
            Function<Candle, Double> customExtractor = c -> c.getCloseAsDouble() + 5.0;

            SimpleStrengthCalculator calc = new SimpleStrengthCalculator(10.0, 30.0, customExtractor);
            double strength = calc.calculate(level, candles);

            double currentPrice = 105.0 + 5.0; // 110.0
            double distance = Math.abs(100.0 - 110.0) / 110.0;
            double priceFactor = Math.exp(-distance * 10.0);

            double durationWeight = Math.log(11 + 1);
            double timeFactor = Math.exp(-5.0 / 30.0);
            double expected = 1 * durationWeight * priceFactor * timeFactor;

            assertEquals(expected, strength, 0.001);
        }
    }
}
