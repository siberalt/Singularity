package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для SlopeUpsideCalculator.
 * Сравнивает наклон последних period свечей с средней дельтой остальных свечей.
 */
class SlopeUpsideCalculatorTest {

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
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(10, 0.5);

            List<Candle> candles = generateTrendCandles(15, 100.0, 110.0);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным для восходящего тренда");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен создавать с параметрами по умолчанию")
        void shouldCreateWithDefaults() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator();

            List<Candle> candles = generateTrendCandles(15, 100.0, 110.0);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }

        @Test
        @DisplayName("Должен создавать с кастомным периодом")
        void shouldCreateWithCustomPeriod() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(20, 0.4);

            List<Candle> candles = generateTrendCandles(25, 100.0, 120.0);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }
    }

    @Nested
    @DisplayName("Калькуляция наклона и Upside")
    class SlopeCalculation {

        @Test
        @DisplayName("Должен возвращать NEUTRAL при недостаточном количестве свечей")
        void shouldReturnNeutralWithInsufficientCandles() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator();

            List<Candle> candles = generateTrendCandles(10, 100.0, 105.0);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result, "Нужно минимум period+1 = 15 свечей");
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при null-списке")
        void shouldReturnNeutralForNullCandles() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator();

            Upside result = calculator.calculate(null);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при низком R² (плохая линейная зависимость)")
        void shouldReturnNeutralForLowR2() {
            // Создаем zigzag паттерн (без тренда) - чередуем цены
            List<Candle> candles = generateZigzagCandles(15, 100.0, 114.0);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.8);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result, "Должен вернуть NEUTRAL при R² < minR2");
        }

        @Test
        @DisplayName("Должен возвращать положительный сигнал для восходящего тренда")
        void shouldReturnPositiveSignalForUpTrend() {
            List<Candle> candles = generateTrendCandles(15, 100.0, 110.0);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.4);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным для восходящего тренда");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен возвращать отрицательный сигнал для нисходящего тренда")
        void shouldReturnNegativeSignalForDownTrend() {
            List<Candle> candles = generateTrendCandles(15, 110.0, 100.0);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.4);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() < 0, "Signal должен быть отрицательным для нисходящего тренда");
            assertTrue(result.strength() > 0, "Strength должна быть положительной");
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL для плоского рынка с плохим R²")
        void shouldReturnNeutralForFlatMarket() {
            // Все свечи по закрытию одинаковые - R² = 0 (нет линейной зависимости)
            List<Candle> candles = generateFlatCandles(15, 100.0);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.4);
            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен возвращать максимальный signal при очень сильном восходящем тренде на последних свечах")
        void shouldReturnMaxSignalForStrongUpTrend() {
            // 20 старых свечей с небольшими колебаниями, 5 новых с сильным восходящим трендом
            List<Candle> flatCandles = generateFlatCandles(20, 100.0);
            List<Candle> strongUpCandles = generateTrendCandles(5, 100.0, 125.0);
            List<Candle> allCandles = new ArrayList<>(flatCandles.size() + strongUpCandles.size());
            allCandles.addAll(flatCandles);
            allCandles.addAll(strongUpCandles);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(5, 0.9);
            Upside result = calculator.calculate(allCandles);

            // Signal должен быть близок к 1.0, так как наклон последних 5 гораздо больше avgDelta других
            assertTrue(result.signal() > 0.9, "Signal должен быть близок к 1.0: " + result.signal());
            assertEquals(1.0, result.strength(), 0.01, "Strength должна быть близка к 1.0");
        }

        @Test
        @DisplayName("Должен возвращать минимальный signal при очень сильном нисходящем тренде на последних свечах")
        void shouldReturnMinSignalForStrongDownTrend() {
            // 20 старых свечей с небольшими колебаниями, 5 новых с сильным нисходящим трендом
            List<Candle> flatCandles = generateFlatCandles(20, 110.0);
            List<Candle> strongDownCandles = generateTrendCandles(5, 110.0, 85.0);
            List<Candle> allCandles = new ArrayList<>(flatCandles.size() + strongDownCandles.size());
            allCandles.addAll(flatCandles);
            allCandles.addAll(strongDownCandles);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(5, 0.9);
            Upside result = calculator.calculate(allCandles);

            // Signal должен быть близок к -1.0, так как наклон последних 5 гораздо меньше avgDelta других
            assertTrue(result.signal() < -0.9, "Signal должен быть близок к -1.0: " + result.signal());
            assertEquals(1.0, result.strength(), 0.01, "Strength должна быть близка к 1.0");
        }

        @Test
        @DisplayName("Должен усиливать strength при высоком R²")
        void shouldBoostStrengthForHighR2() {
            List<Candle> candles = generateTrendCandles(15, 100.0, 110.0);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.4);
            Upside result = calculator.calculate(candles);

            // R² для идеальной линии = 1.0, strength = min(1.0, 1.0 * 1.2) = 1.0
            assertTrue(result.strength() >= 0.8, "Strength должна быть усиленной: " + result.strength());
        }

        @Test
        @DisplayName("Должен нормализовать signal в диапазон [-1, 1]")
        void shouldNormalizeSignalToRange() {
            // 20 старых свечей с небольшими колебаниями, 5 новых с восходящим трендом
            List<Candle> flatCandles = generateFlatCandles(20, 100.0);
            List<Candle> strongUpCandles = generateTrendCandles(5, 100.0, 120.0);
            List<Candle> allCandles = new ArrayList<>(flatCandles.size() + strongUpCandles.size());
            allCandles.addAll(flatCandles);
            allCandles.addAll(strongUpCandles);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(5, 0.4);
            Upside result = calculator.calculate(allCandles);

            assertTrue(result.signal() >= -1.0 && result.signal() <= 1.0,
                    "Signal должен быть в диапазоне [-1, 1]: " + result.signal());
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Должен обрабатывать малый период (period=2)")
        void shouldHandleMinPeriod() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(2, 0.4);

            // Нужно минимум 3 свечи (period + 1)
            List<Candle> candles = generateTrendCandles(3, 100.0, 105.0);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }

        @Test
        @DisplayName("Должен возвращать NEUTRAL при пустом списке")
        void shouldReturnNeutralForEmptyList() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator();

            Upside result = calculator.calculate(List.of());

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен обрабатывать большие ценовые значения")
        void shouldHandleLargePrices() {
            List<Candle> candles = generateTrendCandles(15, 10000.0, 11000.0);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.4);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }

        @Test
        @DisplayName("Должен обрабатывать малые ценовые значения")
        void shouldHandleSmallPrices() {
            List<Candle> candles = generateTrendCandles(15, 0.01, 0.02);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(14, 0.4);
            Upside result = calculator.calculate(candles);

            assertTrue(result.signal() > 0, "Signal должен быть положительным");
        }

        @Test
        @DisplayName("Должен обрабатывать точно минимальное количество свечей (period + 1)")
        void shouldHandleMinimumCandles() {
            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(3, 0.4);

            // Ровно 4 свечи (period + 1)
            List<Candle> candles = generateTrendCandles(4, 100.0, 110.0);
            Upside result = calculator.calculate(candles);

            // Должен использовать последние 3 и остальные 1 для сравнения
            assertNotEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Должен использовать последние 3 свечи из 25 при period=3")
        void shouldUseLast3From25() {
            // Создаем длинный список с разным наклоном
            List<Candle> downTrend = generateTrendCandles(22, 130.0, 100.0);
            List<Candle> upTrend = generateTrendCandles(3, 100.0, 110.0);
            
            List<Candle> candles = new ArrayList<>(downTrend.size() + upTrend.size());
            candles.addAll(downTrend);
            candles.addAll(upTrend);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(3, 0.4);
            Upside result = calculator.calculate(candles);

            // Должен использовать последние 3 свечи и сравнить с дельтой остальных 22
            assertTrue(result.signal() > 0, "Должен использовать только последние 3 свечи из 25");
        }

        @Test
        @DisplayName("Должен игнорировать старые свечи и брать только последние period=3")
        void shouldOnlyUseLastNCandles() {
            // Создаем длинный список с разным наклоном
            List<Candle> downTrend = generateTrendCandles(10, 120.0, 100.0);
            List<Candle> upTrend = generateTrendCandles(3, 100.0, 110.0);
            
            List<Candle> candles = new ArrayList<>(downTrend.size() + upTrend.size());
            candles.addAll(downTrend);
            candles.addAll(upTrend);

            SlopeUpsideCalculator calculator = new SlopeUpsideCalculator(3, 0.4);
            Upside result = calculator.calculate(candles);

            // Должен использовать только последние 3 свечи и сравнить с дельтой остальных
            assertTrue(result.signal() > 0, "Должен использовать только последние 3 свечи");
        }
    }

    /**
     * Генерирует N свечей с фиксированной ценой close.
     */
    private List<Candle> generateFlatCandles(int n, double closePrice) {
        Candle[] candles = new Candle[n];
        for (int i = 0; i < n; i++) {
            candles[i] = factory.createCommon(closePrice);
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей с восходящим трендом.
     */
    private List<Candle> generateTrendCandles(int n, double startPrice, double endPrice) {
        Candle[] candles = new Candle[n];
        double priceStep = (endPrice - startPrice) / (n - 1);

        for (int i = 0; i < n; i++) {
            double close = startPrice + i * priceStep;
            candles[i] = factory.createCommon(close);
        }
        return List.of(candles);
    }

    /**
     * Генерирует N свечей в zigzag паттерне (без тренда).
     */
    private List<Candle> generateZigzagCandles(int n, double minPrice, double maxPrice) {
        Candle[] candles = new Candle[n];
        for (int i = 0; i < n; i++) {
            // Чередуем цены между min и max для создания zigzag без тренда
            double close = (i % 2 == 0) ? minPrice : maxPrice;
            candles[i] = factory.createCommon(close);
        }
        return List.of(candles);
    }
}
