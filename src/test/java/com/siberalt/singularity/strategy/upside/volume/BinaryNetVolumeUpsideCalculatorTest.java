package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;

import static com.siberalt.singularity.strategy.upside.volume.BinaryNetVolumeUpsideCalculator.DEFAULT_MAX_NOISE_RATIO;
import static org.junit.jupiter.api.Assertions.*;

class BinaryNetVolumeUpsideCalculatorTest {
    /**
     * Вспомогательный метод для создания свечи
     */
    private Candle candle(double open, double high, double low, double close, long volume) {
        return Candle.of(TimePoint.NULL, volume, open, high, low, close);
    }


    @Nested
    @DisplayName("Базовый расчёт")
    class BasicCalculation {

        @Test
        @DisplayName("Должен вернуть 0 при равном объёме вверх/вниз")
        void shouldReturnZeroSignalWhenEqualUpAndDownVolume() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.1, 0.4, DEFAULT_MAX_NOISE_RATIO);

            List<Candle> candles = List.of(
                candle(100.0, 105.0, 100.0, 105.0, 1000), // up
                candle(105.0, 110.0, 105.0, 110.0, 1000), // up
                candle(110.0, 110.0, 105.0, 105.0, 1000), // down
                candle(105.0, 110.0, 105.0, 100.0, 1000)  // down
            );

            Upside result = calc.calculate(candles);

            assertEquals(0.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("Должен вернуть 1.0 при полном доминировании up")
        void shouldReturnOneWhenOnlyUpVolumes() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.1, 0.4, DEFAULT_MAX_NOISE_RATIO);

            List<Candle> candles = List.of(
                candle(100.0, 105.0, 100.0, 105.0, 1000),
                candle(105.0, 110.0, 105.0, 110.0, 2000)
            );

            Upside result = calc.calculate(candles);

            assertEquals(1.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("Должен вернуть -1.0 при полном доминировании down")
        void shouldReturnMinusOneWhenOnlyDownVolumes() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.1, 0.4, DEFAULT_MAX_NOISE_RATIO);

            List<Candle> candles = List.of(
                candle(110.0, 110.0, 105.0, 105.0, 2000),
                candle(105.0, 110.0, 105.0, 105.0, 1000)
            );

            Upside result = calc.calculate(candles);

            assertTrue(result.signal() < -0.5);
        }
    }

    @Nested
    @DisplayName("Влияние bodyWeightFactor")
    class BodyWeightInfluence {

        @Test
        @DisplayName("Сильное тело должно увеличивать вес объёма")
        void strongBodyShouldIncreaseWeight() {
            // Без веса: up = 1000*1.0 + 2000*1.0 = 3000, down = 1000*1.0 → signal = 0.5
            // С весом: up-свечи с bodyRatio=1.0 получают вес 1.4
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.0, 1.0, DEFAULT_MAX_NOISE_RATIO);

            List<Candle> candles = List.of(
                candle(100.0, 110.0, 100.0, 110.0, 1000), // strong up
                candle(110.0, 110.0, 105.0, 105.0, 1000)  // down
            );

            Upside result = calc.calculate(candles);

            // weight_up = 1.0 + 1.0 * (1.0 - 0) / 1.0 = 2.0
            // up = 1000 * 2.0 = 2000
            // down = 1000 * 1.0 = 1000
            // signal = (2000 - 1000) / 3000 = 0.333
            assertEquals(0.0, result.signal(), 0.01);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null или пустом списке — возвращает NEUTRAL")
        void shouldReturnNeutralOnEmptyInput() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator();

            assertEquals(Upside.NEUTRAL, calc.calculate(null));
            assertEquals(Upside.NEUTRAL, calc.calculate(Collections.emptyList()));
        }

        @Test
        @DisplayName("При нулевом объёме — свеча пропускается")
        void shouldSkipZeroVolumeCandles() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.1, 0.4, DEFAULT_MAX_NOISE_RATIO);

            List<Candle> candles = List.of(
                candle(100.0, 105.0, 100.0, 105.0, 1000),
                candle(105.0, 110.0, 105.0, 110.0, 0) // zero volume → skip
            );

            Upside result = calc.calculate(candles);
            assertTrue(result.signal() > 0);
        }

        @Test
        @DisplayName("При range = 0 (flat) — bodyRatio = 1.0")
        void shouldSetBodyRatioToOneWhenRangeIsZero() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.1, 0.4, DEFAULT_MAX_NOISE_RATIO);

            // flat: open=high=low=close
            List<Candle> candles = List.of(
                candle(100.0, 100.0, 100.0, 100.0, 1000),
                candle(100.0, 100.0, 100.0, 105.0, 2000)
            );

            Upside result = calc.calculate(candles);
            assertEquals(1.0, result.signal(), 0.001);
        }

        @Test
        @DisplayName("При totalVolume = 0 — возвращает NEUTRAL")
        void shouldReturnNeutralWhenTotalVolumeIsZero() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator();

            List<Candle> candles = List.of(
                candle(100.0, 105.0, 100.0, 105.0, 0),
                candle(105.0, 110.0, 105.0, 110.0, 0)
            );

            Upside result = calc.calculate(candles);
            assertEquals(0.0, result.signal(), 0.001);
            assertEquals(0.0, result.strength(), 0.001);
        }
    }

    @Nested
    @DisplayName("Коррекция при высоком шуме")
    class NoiseCorrection {

        @Test
        @DisplayName("При noise > maxNoiseRatio — signal и strength уменьшаются")
        void shouldReduceSignalAndStrengthWhenNoiseExceedsThreshold() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.5, 1.0, 0.5);

            // 10 свечей, но только 3 значимые (noise = 70% > 50%)
            List<Candle> candles = new ArrayList<>();
            candles.add(candle(100.0, 105.0, 100.0, 105.0, 1000)); // up
            candles.add(candle(105.0, 110.0, 105.0, 110.0, 2000)); // up
            candles.add(candle(110.0, 110.0, 105.0, 105.0, 1000)); // down
            for (int i = 0; i < 7; i++) {
                candles.add(candle(100.0, 100.1, 100.0, 100.0, 10)); // very weak, flat
            }

            Upside result = calc.calculate(candles);

            assertEquals(0.3, result.signal(), 0.02);
            assertEquals(0.18, result.strength(), 0.02);
        }

        @Test
        @DisplayName("При noise <= maxNoiseRatio — без коррекции")
        void shouldNotCorrectWhenNoiseIsAcceptable() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(0.0, 0.0, 0.9);

            List<Candle> candles = List.of(
                candle(100.0, 105.0, 100.0, 105.0, 1000),
                candle(105.0, 110.0, 105.0, 110.0, 1000),
                candle(110.0, 110.0, 105.0, 105.0, 1000)
            );

            Upside result = calc.calculate(candles);

            assertEquals(1.0/3.0, result.signal(), 0.001);
            assertEquals(1.0, result.strength(), 0.001);
        }
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {

        @Test
        @DisplayName("Конструктор по умолчанию должен инициализировать дефолтами")
        void shouldInitializeWithDefaults() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator();

            assertEquals(0.08, calc.minBodyRatio(), 0.001);
            assertEquals(0.4, calc.bodyWeightFactor(), 0.001);
            assertEquals(0.7, calc.maxNoiseRatio(), 0.001);
            assertFalse(calc.ignoreLowBody());
        }

        @Test
        @DisplayName("Минимальные значения не могут быть отрицательными")
        void shouldNotAllowNegativeValues() {
            BinaryNetVolumeUpsideCalculator calc = new BinaryNetVolumeUpsideCalculator(-1.0, -2.0, -3.0);

            assertEquals(0.0, calc.minBodyRatio(), 0.001);
            assertEquals(0.0, calc.bodyWeightFactor(), 0.001);
            assertEquals(0.7, calc.maxNoiseRatio(), 0.001);
        }
    }
}
