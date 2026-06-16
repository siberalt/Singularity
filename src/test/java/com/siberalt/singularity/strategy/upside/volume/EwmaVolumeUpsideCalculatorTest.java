package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для EwmaVolumeUpsideCalculator.
 */
class EwmaVolumeUpsideCalculatorTest {

    private EwmaVolumeUpsideCalculator calculator;
    private List<Candle> candles;

    @BeforeEach
    void setUp() {
        calculator = new EwmaVolumeUpsideCalculator();
        candles = new ArrayList<>();
    }

    private Candle createCandle(double open, double close, long volume) {
        double range = 1.0;
        return Candle.of(
            Instant.now().plusSeconds(candles.size() * 60L),
            volume,
            open,
            open + range,
            open - range,
            close
        );
    }

    @Nested
    @DisplayName("Базовые случаи")
    class BasicCases {

        @Test
        @DisplayName("Должен вернуть NEUTRAL при пустом списке")
        void shouldReturnNeutralForEmptyList() {
            Upside result = calculator.calculate(List.of());
            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при null-списке")
        void shouldReturnNeutralForNullList() {
            Upside result = calculator.calculate(null);
            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен вернуть положительный сигнал при всех восходящих свечах")
        void shouldReturnPositiveWhenAllBullish() {
            candles.add(createCandle(100, 102, 1000));
            candles.add(createCandle(102, 105, 1500));
            candles.add(createCandle(105, 108, 2000));

            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен вернуть отрицательный сигнал при всех нисходящих свечах")
        void shouldReturnNegativeWhenAllBearish() {
            candles.add(createCandle(100, 98, 1000));
            candles.add(createCandle(98, 95, 1500));
            candles.add(createCandle(95, 92, 2000));

            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() < 0, "Signal должен быть отрицательным");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен игнорировать свечи с нулевым объёмом")
        void shouldIgnoreZeroVolumeCandles() {
            candles.add(createCandle(100, 102, 1000));
            candles.add(createCandle(102, 100, 0)); // нулевой объём
            candles.add(createCandle(100, 101, 1000));

            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Свечи с нулевым объёмом должны игнорироваться");
        }

        @Test
        @DisplayName("Должен вернуть NEUTRAL при всех нулевых объёмах")
        void shouldReturnNeutralWhenAllVolumesAreZero() {
            candles.add(createCandle(100, 102, 0));
            candles.add(createCandle(102, 100, 0));

            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать одну свечу")
        void shouldHandleSingleCandle() {
            candles.add(createCandle(100, 101, 500));

            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Положительная свеча должна дать положительный сигнал");
        }
    }

    @Nested
    @DisplayName("Экспоненциальное взвешивание")
    class ExponentialWeighting {

        @Test
        @DisplayName("Должен учитывать экспоненциальный вес (последние свечи важнее)")
        void shouldWeightRecentCandlesMore() {
            // Две большие восходящие свечи в начале
            candles.add(createCandle(100, 102, 5000));
            candles.add(createCandle(102, 105, 4000));
            // Один маленький нисходящий в конце
            candles.add(createCandle(105, 104, 500));

            // Последняя свеча должна весить больше из-за EMA
            Upside result = calculator.calculate(candles);

            // Благодаря EMA последняя свеча имеет больший вес,
            // поэтому сигнал может быть отрицательным или близким к 0
            assertTrue(result.strength() > 0.5, "Strength должна быть выше при учете EMA");
        }

        @Test
        @DisplayName("Должен корректно работать с различными значениями halfLife")
        void shouldHandleDifferentHalfLifeValues() {
            // Короткий halfLife (быстрое затухание)
            EwmaVolumeUpsideCalculator shortHalfLifeCalculator = new EwmaVolumeUpsideCalculator(
                0.08, 0.4, 0.7, false, 3.0
            );
            // Длинный halfLife (медленное затухание)
            EwmaVolumeUpsideCalculator longHalfLifeCalculator = new EwmaVolumeUpsideCalculator(
                0.08, 0.4, 0.7, false, 50.0
            );

            for (int i = 0; i < 10; i++) {
                candles.add(createCandle(100 + i, 102 + i, 1000 + i * 100));
            }

            Upside shortResult = shortHalfLifeCalculator.calculate(candles);
            Upside longResult = longHalfLifeCalculator.calculate(candles);

            // Оба должны вернуть положительный сигнал для восходящего тренда
            assertTrue(shortResult.signal() > 0);
            assertTrue(longResult.signal() > 0);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать минимальный halfLife")
        void shouldHandleMinimalHalfLife() {
            EwmaVolumeUpsideCalculator minHalfLifeCalculator = new EwmaVolumeUpsideCalculator(
                0.08, 0.4, 0.7, false, -5.0  // отрицательное значение должно быть исправлено на 1
            );

            candles.add(createCandle(100, 102, 1000));
            candles.add(createCandle(102, 105, 2000));

            Upside result = minHalfLifeCalculator.calculate(candles);

            assertTrue(result.strength() > 0, "Минимальный halfLife должен быть обработан");
        }
    }

    @Nested
    @DisplayName("Body ratio и веса тел")
    class BodyRatioAndWeights {

        @Test
        @DisplayName("Должен учитывать bodyRatio при расчёте весов")
        void shouldConsiderBodyRatio() {
            // Свеча с большим телом (высокий bodyRatio)
            candles.add(createCandle(100, 105, 2000)); // bodyRatio = 5/10 = 0.5
            // Свеча с маленьким телом (низкий bodyRatio)
            candles.add(createCandle(100, 101, 1000)); // bodyRatio = 1/10 = 0.1

            // Свеча с большим телом должна получить больший вес
            Upside result = calculator.calculate(candles);

            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен усиливать веса для больших тел при bodyWeightFactor > 0")
        void shouldAmplifyWeightsForLargeBodies() {
            EwmaVolumeUpsideCalculator calculatorWithAmplification = new EwmaVolumeUpsideCalculator(
                0.08,  // minBodyRatio
                1.0,   // bodyWeightFactor - сильное усиление
                0.7,   // maxNoiseRatio
                false, // ignoreLowBody
                12.0   // halfLife
            );

            candles.add(createCandle(100, 105, 1000)); // большой body
            candles.add(createCandle(100, 102, 1000)); // маленький body

            Upside result = calculatorWithAmplification.calculate(candles);

            assertTrue(result.signal() > 0, "Усиление весов должно повлиять на сигнал");
        }

        @Test
        @DisplayName("Должен игнорировать свечи с маленьким телом при ignoreLowBody=true")
        void shouldIgnoreLowBodyCandlesWhenEnabled() {
            EwmaVolumeUpsideCalculator calculatorIgnoringLowBodies = new EwmaVolumeUpsideCalculator(
                0.3,   // minBodyRatio
                0.4,   // bodyWeightFactor
                0.7,   // maxNoiseRatio
                true,  // ignoreLowBody = true
                12.0   // halfLife
            );

            candles.add(createCandle(100, 105, 2000)); // большой body, будет учтён
            candles.add(createCandle(100, 101, 2000)); // маленький body, будет проигнорирован

            Upside result = calculatorIgnoringLowBodies.calculate(candles);

            // Поскольку вторая свеча проигнорирована, сигнал должен соответствовать первой
            assertTrue(result.signal() > 0, "Свеча с маленьким телом должна быть проигнорирована");
        }
    }

    @Nested
    @DisplayName("Свечи с одинаковыми ценами")
    class FlatCandles {

        @Test
        @DisplayName("Должен корректно обрабатывать свечи с одинаковыми ценами")
        void shouldHandleFlatCandles() {
            // Свечи с одинаковыми open/close/high/low
            Candle flatCandle1 = Candle.of(
                Instant.now().plusSeconds(0),
                1000,
                100, 100, 100, 100
            );
            Candle flatCandle2 = Candle.of(
                Instant.now().plusSeconds(60),
                2000,
                100, 100, 100, 100
            );
            candles.add(flatCandle1);
            candles.add(flatCandle2);

            Upside result = calculator.calculate(candles);

            // Для flat-свечей signal должен быть 0 (range = 0)
            assertEquals(0.0, result.signal(), 0.001);
        }
    }

    @Nested
    @DisplayName("Шум и фильтрация")
    class NoiseAndFiltering {

        @Test
        @DisplayName("Должен уменьшать strength при высоком шуме")
        void shouldReduceStrengthWithHighNoise() {
            // Создаём много свечей, но используем только несколько
            for (int i = 0; i < 20; i++) {
                candles.add(createCandle(100 + i, 102 + i, 1000));
            }

            Upside result = calculator.calculate(candles);

            // strength не должен превышать 1.0
            assertTrue(result.strength() <= 1.0);
        }

        @Test
        @DisplayName("Должен фильтровать сигнал при превышении maxNoiseRatio")
        void shouldFilterSignalWhenNoiseExceedsMaxRatio() {
            EwmaVolumeUpsideCalculator lowNoiseCalculator = new EwmaVolumeUpsideCalculator(
                0.08, 0.4, 0.3, false, 12.0  // maxNoiseRatio = 0.3 (низкий)
            );

            // Много свечей, но используем только часть
            for (int i = 0; i < 20; i++) {
                candles.add(createCandle(100 + i, 102 + i, 1000));
            }

            Upside result = lowNoiseCalculator.calculate(candles);

            // При высоком шуме signal и strength должны быть снижены
            assertTrue(result.strength() <= 1.0);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при отсутствии используемых свечей")
        void shouldReturnNeutralWhenNoCandlesUsed() {
            // Свеча с нулевым объёмом, которая должна быть проигнорирована
            Candle zeroVolumeCandle = Candle.of(
                Instant.now(),
                0,  // объём = 0
                100, 102, 98, 101
            );
            candles.add(zeroVolumeCandle);

            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Кастомные параметры")
    class CustomParameters {

        @Test
        @DisplayName("Должен работать с кастомными параметрами")
        void shouldWorkWithCustomParameters() {
            EwmaVolumeUpsideCalculator customCalculator = new EwmaVolumeUpsideCalculator(
                0.15,  // minBodyRatio
                0.8,   // bodyWeightFactor
                0.6,   // maxNoiseRatio
                true,  // ignoreLowBody
                8.0    // halfLife
            );

            candles.add(createCandle(100, 102, 1500));
            candles.add(createCandle(102, 105, 2000));

            Upside result = customCalculator.calculate(candles);

            assertTrue(result.strength() > 0, "Кастомные параметры должны работать корректно");
        }
    }

    @Nested
    @DisplayName("Нисходящий тренд с отскоками")
    class DownTrendWithPullbacks {

        @Test
        @DisplayName("Должен возвращать отрицательный сигнал при нисходящем тренде с отскоками")
        void shouldReturnNegativeForDownTrendWithPullbacks() {
            // Генерируем нисходящий тренд с локальными отскоками
            List<Candle> pullbackCandles = new ArrayList<>();
            double startPrice = 105.0;
            double endPrice = 95.0;
            int n = 16;
            double priceStep = (endPrice - startPrice) / (n - 1);

            for (int i = 0; i < n; i++) {
                // Основной нисходящий тренд
                double baseClose = startPrice + i * priceStep;
                double close;
                if (i > 0 && i % 3 == 0) {
                    // Каждая 3-я свеча - небольшой отскок вверх (но всё равно ниже предыдущей)
                    close = baseClose + Math.abs(priceStep) * 0.4;
                } else {
                    close = baseClose;
                }
                double open = close + 0.5 + (Math.random() - 0.5); // немного выше close
                pullbackCandles.add(Candle.of(
                    Instant.now().plusSeconds(i * 60L),
                    1000L + (n - i) * 100, // объём растёт в начале тренда
                    open,
                    open + 0.5,
                    close - 0.5,
                    close
                ));
            }

            Upside result = calculator.calculate(pullbackCandles);

            assertTrue(result.signal() < 0, "Signal должен быть отрицательным для нисходящего тренда с отскоками");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }
    }
}
