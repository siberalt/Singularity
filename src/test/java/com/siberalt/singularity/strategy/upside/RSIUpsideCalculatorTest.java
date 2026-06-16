package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для RSIUpsideCalculator.
 */
class RSIUpsideCalculatorTest {

    private CandleFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CandleFactory("TEST");
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {

        @Test
        @DisplayName("Должен создавать с полными настройками")
        void shouldCreateWithFullSettings() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(10, 25.0, 75.0);

            // Для flat-рынка сигнал должен быть близок к 0
            List<Candle> candles = generateCandles(11, 100.0);
            Upside result = calculator.calculate(candles);
            assertEquals(0.0, result.signal(), 0.01);
        }

        @Test
        @DisplayName("Должен создавать с периодом по умолчанию и стандартными порогами")
        void shouldCreateWithDefaults() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator();

            List<Candle> candles = generateCandles(15, 100.0);
            Upside result = calculator.calculate(candles);
            assertEquals(0.0, result.signal(), 0.01);
        }

        @Test
        @DisplayName("Должен создавать с кастомным периодом и стандартными порогами 30/70")
        void shouldCreateWithCustomPeriod() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(20);

            List<Candle> candles = generateCandles(21, 100.0);
            Upside result = calculator.calculate(candles);
            assertEquals(0.0, result.signal(), 0.01);
        }

        @Test
        @DisplayName("Должен выбрасывать исключение при period < 2")
        void shouldThrowOnInvalidPeriod() {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new RSIUpsideCalculator(1)
            );

            assertTrue(exception.getMessage().contains("Period"));
        }

        @Test
        @DisplayName("Должен ограничивать пороги перепроданности в диапазоне [0, 100]")
        void shouldClampOversoldThreshold() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14, -10.0, 80.0);

            // Порог должен быть приведен к 0, но сигнал зависит от RSI, а не от порога
            // Проверяем, что калькулятор работает с клампнутым значением
            List<Candle> candles = generateCandles(15, 100.0);
            Upside result = calculator.calculate(candles);
            // RSI=50 для flat-рынка, сигнал должен быть 0
            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен ограничивать пороги перекупленности в диапазоне [0, 100]")
        void shouldClampOverboughtThreshold() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14, 20.0, 120.0);

            // Порог должен быть приведен к 100, но сигнал зависит от RSI
            List<Candle> candles = generateCandles(15, 100.0);
            Upside result = calculator.calculate(candles);
            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Калькуляция RSI и Upside")
    class RsiCalculation {

        @Test
        @DisplayName("Должен возвращать NEUTRAL при недостаточном количестве свечей")
        void shouldReturnNeutralWithInsufficientCandles() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);

            // Для period=14 нужно минимум 15 свечей
            List<Candle> candles = generateCandles(10, 100.0);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при null-списке")
        void shouldReturnNeutralForNullCandles() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);

            Upside result = calculator.calculate(null);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен возвращать нулевой сигнал при RSI=50 (нейтральная зона)")
        void shouldReturnZeroSignalForRsi50() {
            // Создаем свечи с небольшими колебаниями вокруг 100
            List<Candle> candles = generateVolatileCandles(15, 100.0, 1.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertEquals(0.0, result.signal(), 0.01);
            assertTrue(result.strength() < 0.1, "Strength должна быть близка к 0");
        }

        @Test
        @DisplayName("Должен возвращать положительный сигнал при RSI > 50")
        void shouldReturnPositiveSignalForRsiAbove50() {
            // Создаем восходящий тренд
            List<Candle> candles = generateUpTrendCandles(16, 95.0, 105.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен возвращать отрицательный сигнал при RSI < 50")
        void shouldReturnNegativeSignalForRsiBelow50() {
            // Создаем нисходящий тренд
            List<Candle> candles = generateDownTrendCandles(16, 105.0, 95.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() < 0, "Signal должен быть отрицательным");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен возвращать отрицательный сигнал при нисходящем тренде с отскоками")
        void shouldReturnNegativeSignalForDownTrendWithPullbacks() {
            // Создаем нисходящий тренд с локальными отскоками
            List<Candle> candles = generateDownTrendWithPullbacks(16, 105.0, 95.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() < 0, "Signal должен быть отрицательным для нисходящего тренда");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен возвращать signal=1 при сильном восходящем тренде (RSI=100)")
        void shouldReturnMaxSignalForStrongUpTrend() {
            // Создаем очень сильный восходящий тренд
            List<Candle> candles = generateStrongUpTrendCandles(16, 100.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertEquals(1.0, result.signal(), 0.01);
            assertEquals(1.0, result.strength(), 0.01);
        }

        @Test
        @DisplayName("Должен возвращать signal=-1 при сильном нисходящем тренде (RSI=0)")
        void shouldReturnMinSignalForStrongDownTrend() {
            // Создаем очень сильный нисходящий тренд
            List<Candle> candles = generateStrongDownTrendCandles(16, 100.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertEquals(-1.0, result.signal(), 0.01);
            assertEquals(1.0, result.strength(), 0.01);
        }

        @Test
        @DisplayName("Должен усиливать strength при пересечении порогов перекупленности/перепроданности")
        void shouldBoostStrengthAtExtremeLevels() {
            // Создаем свечи, чтобы RSI был близок к экстремумам
            List<Candle> candles = generateStrongUpTrendCandles(16, 100.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14, 30.0, 70.0);
            Upside result = calculator.calculate(candles);

            // Сила должна быть увеличена на 0.2 при достижении экстремума
            assertTrue(result.strength() >= 0.8, "Strength должна быть усиленной: " + result.strength());
        }

        @Test
        @DisplayName("Должен корректно обрабатывать RSI=0 (RSI=0 при сплошных убытках)")
        void shouldHandleRsiZero() {
            List<Candle> candles = generateDownTrendCandles(16, 100.0, 80.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertEquals(-1.0, result.signal(), 0.01);
        }

        @Test
        @DisplayName("Должен корректно обрабатывать RSI=100 (RSI=100 при сплошных приростах)")
        void shouldHandleRsiHundred() {
            List<Candle> candles = generateUpTrendCandles(16, 80.0, 100.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertEquals(1.0, result.signal(), 0.01);
        }

        @Test
        @DisplayName("Должен возвращать нейтральный результат при RSI=50 (без изменений)")
        void shouldReturnNeutralForFlatMarket() {
            // Все свечи по закрытию одинаковые
            List<Candle> candles = generateCandles(16, 100.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            // RSI будет 50 (нет ни приростов ни убытков)
            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Должен обрабатывать малый период (period=2)")
        void shouldHandleMinPeriod() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(2);

            // Для period=2 нужно минимум 3 свечи
            List<Candle> candles = generateUpTrendCandles(3, 95.0, 105.0);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным для восходящего тренда");
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при пустом списке")
        void shouldReturnNeutralForEmptyList() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);

            Upside result = calculator.calculate(List.of());

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен обрабатывать большие ценовые значения")
        void shouldHandleLargePrices() {
            List<Candle> candles = generateUpTrendCandles(16, 10000.0, 11000.0);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }

        @Test
        @DisplayName("Должен обрабатывать малые ценовые значения")
        void shouldHandleSmallPrices() {
            List<Candle> candles = generateUpTrendCandles(16, 0.01, 0.02);

            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }

        @Test
        @DisplayName("Должен обрабатывать единичную свечу при недостатке данных")
        void shouldHandleSingleCandle() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);

            List<Candle> candles = List.of(factory.createCommon("2021-01-01T00:01:00Z", 100.0));
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен обрабатывать точно минимальное количество свечей")
        void shouldHandleMinimumCandles() {
            RSIUpsideCalculator calculator = new RSIUpsideCalculator(14);

            // Ровно 15 свечей (period + 1)
            List<Candle> candles = generateUpTrendCandles(15, 95.0, 105.0);
            Upside result = calculator.calculate(candles);

            // Не должен возвращать NEUTRAL
            assertNotEquals(Upside.NEUTRAL, result);
        }
    }

    /**
     * Генерирует N свечей с фиксированной ценой close.
     */
    private List<Candle> generateCandles(int n, double closePrice) {
        Candle[] candles = new Candle[n];
        for (int i = 0; i < n; i++) {
            candles[i] = factory.createCommon("2021-01-" + String.format("%02d", i + 1) + "T00:00:00Z", closePrice);
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей с волатильностью вокруг базового значения.
     */
    private List<Candle> generateVolatileCandles(int n, double basePrice, double volatility) {
        Candle[] candles = new Candle[n];
        double price = basePrice;
        for (int i = 0; i < n; i++) {
            // Используем симметричные колебания для приближения к RSI=50
            double close = price + (i % 2 == 0 ? volatility : -volatility);
            candles[i] = factory.createCommon("2021-01-" + String.format("%02d", i + 1) + "T00:00:00Z", close);
            price = close;
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей с восходящим трендом.
     */
    private List<Candle> generateUpTrendCandles(int n, double startPrice, double endPrice) {
        Candle[] candles = new Candle[n];
        double priceStep = (endPrice - startPrice) / (n - 1);

        for (int i = 0; i < n; i++) {
            double close = startPrice + i * priceStep;
            candles[i] = factory.createCommon("2021-01-" + String.format("%02d", i + 1) + "T00:00:00Z", close);
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей с нисходящим трендом.
     */
    private List<Candle> generateDownTrendCandles(int n, double startPrice, double endPrice) {
        return generateUpTrendCandles(n, startPrice, endPrice);
    }

    /**
     * Генерирует N свечей с нисходящим трендом, но с некоторыми восходящими свечами.
     * Это создает более реалистичную картину - тренд вниз, но с локальными отскоками.
     */
    private List<Candle> generateDownTrendWithPullbacks(int n, double startPrice, double endPrice) {
        Candle[] candles = new Candle[n];
        double priceStep = (endPrice - startPrice) / (n - 1);

        for (int i = 0; i < n; i++) {
            // Большинство свечей идут вниз, но каждая 3-я немного отскакивает вверх
            double close;
            if (i > 0 && i % 3 == 0) {
                // Каждая 3-я свеча - небольшой отскок вверх
                close = startPrice + i * priceStep + Math.abs(priceStep) * 0.3;
            } else {
                // Основной нисходящий тренд
                close = startPrice + i * priceStep;
            }
            candles[i] = factory.createCommon("2021-01-" + String.format("%02d", i + 1) + "T00:00:00Z", close);
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей с очень сильным восходящим трендом (RSI близко к 100).
     */
    private List<Candle> generateStrongUpTrendCandles(int n, double startPrice) {
        Candle[] candles = new Candle[n];
        double price = startPrice;

        for (int i = 0; i < n; i++) {
            // Каждая свеча закрывается значительно выше предыдущей
            double close = price * (1.0 + 0.05 + i * 0.01);
            candles[i] = factory.createCommon("2021-01-" + String.format("%02d", i + 1) + "T00:00:00Z", close);
            price = close;
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей с очень сильным нисходящим трендом (RSI близко к 0).
     */
    private List<Candle> generateStrongDownTrendCandles(int n, double startPrice) {
        Candle[] candles = new Candle[n];
        double price = startPrice;

        for (int i = 0; i < n; i++) {
            // Каждая свеча закрывается значительно ниже предыдущей
            double close = price * (1.0 - 0.05 - i * 0.01);
            candles[i] = factory.createCommon("2021-01-" + String.format("%02d", i + 1) + "T00:00:00Z", close);
            price = close;
        }
        return List.of(candles);
    }
}
