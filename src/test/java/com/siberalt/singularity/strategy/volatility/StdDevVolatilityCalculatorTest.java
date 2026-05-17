package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StdDevVolatilityCalculatorTest {

    // Простая заглушка Candle
    private Candle candle(double price) {
        return candle(price, price, price, price);
    }

    private Candle candle(double open, double high, double low, double close) {
        return Candle.of(TimePoint.NULL, 0, open, high, low, close);
    }

    @Nested
    @DisplayName("Базовые вычисления")
    class BasicCalculations {

        @Test
        @DisplayName("Должен корректно рассчитать StdDev для 5 значений")
        void shouldCalculateStdDevCorrectly() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(102.0),
                candle(104.0),
                candle(106.0),
                candle(108.0)
            );

            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(5);

            double result = calc.calculate(candles);

            // Среднее = 104.0
            // Дисперсия (n-1) = ((-4)^2 + (-2)^2 + 0^2 + 2^2 + 4^2) / 4 = (16+4+0+4+16)/4 = 40/4 = 10
            // StdDev = sqrt(10) ≈ 3.162
            assertEquals(Math.sqrt(10), result, 0.001);
        }

        @Test
        @DisplayName("Для двух одинаковых цен — StdDev = 0")
        void twoIdenticalPricesShouldGiveZeroStdDev() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(100.0)
            );

            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(2);
            double result = calc.calculate(candles);

            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("Для двух разных цен — корректное значение")
        void twoDifferentPricesShouldGiveCorrectStdDev() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(104.0)
            );

            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(2);
            double result = calc.calculate(candles);

            // mean = 102
            // variance = ((-2)^2 + (2)^2) / (2-1) = (4 + 4) / 1 = 8
            // std = sqrt(8) ≈ 2.828
            assertEquals(Math.sqrt(8), result, 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null списке — возвращает 0.0")
        void shouldReturnZeroOnNull() {
            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(5);
            double result = calc.calculate(null);
            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("При пустом списке — возвращает 0.0")
        void shouldReturnZeroOnEmptyList() {
            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(5);
            double result = calc.calculate(Collections.emptyList());
            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("Если свечей меньше периода — возвращает 0.0")
        void shouldReturnZeroIfNotEnoughData() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(101.0)
            ); // 2 < period=3

            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(3);
            double result = calc.calculate(candles);

            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("Период = 2 — минимально допустимый")
        void shouldWorkWithMinPeriod() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(105.0)
            );

            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(2);
            double result = calc.calculate(candles);

            // variance = ( (-2.5)^2 + (2.5)^2 ) / 1 = (6.25 + 6.25) / 1 = 12.5 → std = sqrt(12.5) ≈ 3.535
            assertEquals(Math.sqrt(12.5), result, 0.001);
        }
    }

    @Nested
    @DisplayName("Кастомный PriceExtractor")
    class CustomPriceExtractor {

        @Test
        @DisplayName("Поддерживает извлечение цены по максимуму")
        void shouldWorkWithHighPriceExtractor() {
            List<Candle> candles = Arrays.asList(
                candle(100, 105, 98, 102), // high = 105
                candle(102, 108, 100, 106), // high = 108
                candle(106, 110, 104, 109)  // high = 110
            );

            PriceExtractor highExtractor = Candle::getHighPrice;
            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(3, highExtractor);

            double result = calc.calculate(candles);

            // Цены: [105, 108, 110]
            // mean = 107.666...
            // sumSq = (105-107.67)^2 + (108-107.67)^2 + (110-107.67)^2 ≈ 7.11 + 0.11 + 5.44 = 12.66
            // variance = 12.66 / 2 = 6.33 → std ≈ 2.517
            assertEquals(2.517, result, 0.01);
        }

        @Test
        @DisplayName("Поддерживает извлечение цены по минимуму")
        void shouldWorkWithLowPriceExtractor() {
            List<Candle> candles = Arrays.asList(
                candle(100, 105, 98, 102),
                candle(102, 108, 100, 106),
                candle(106, 110, 104, 109)
            );

            PriceExtractor lowExtractor = Candle::getLowPrice;
            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(3, lowExtractor);

            double result = calc.calculate(candles);

            // Цены: [98, 100, 104]
            // mean = 100.666...
            // sumSq = (98-100.67)^2 + (100-100.67)^2 + (104-100.67)^2 ≈ 7.11 + 0.44 + 11.11 = 18.66
            // variance = 18.66 / 2 = 9.33 → std ≈ 3.055
            assertEquals(3.055, result, 0.01);
        }
    }

    @Nested
    @DisplayName("Конструкторы")
    class Constructors {

        @Test
        @DisplayName("Удобный конструктор использует getClosePrice")
        void defaultConstructorShouldUseClosePrice() {
            List<Candle> candles = Arrays.asList(
                candle(100, 105, 98, 102),
                candle(102, 108, 100, 106),
                candle(106, 110, 104, 109)
            );

            StdDevVolatilityCalculator defaultCalc = new StdDevVolatilityCalculator(3);
            StdDevVolatilityCalculator explicitCalc = new StdDevVolatilityCalculator(3, Candle::getClosePrice);

            double result1 = defaultCalc.calculate(candles);
            double result2 = explicitCalc.calculate(candles);

            assertEquals(result1, result2, 0.001);
        }

        @Test
        @DisplayName("Конструктор должен выбрасывать исключение при period < 2")
        void shouldThrowOnInvalidPeriod() {
            assertThrows(IllegalArgumentException.class, () -> new StdDevVolatilityCalculator(1));
            assertThrows(IllegalArgumentException.class, () -> new StdDevVolatilityCalculator(0));
            assertThrows(IllegalArgumentException.class, () -> new StdDevVolatilityCalculator(-1));
        }
    }

    @Nested
    @DisplayName("Численная устойчивость")
    class NumericalStability {

        @Test
        @DisplayName("Все цены одинаковы — StdDev = 0")
        void allPricesEqualShouldGiveZero() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(100.0),
                candle(100.0),
                candle(100.0),
                candle(100.0)
            );

            StdDevVolatilityCalculator calc = new StdDevVolatilityCalculator(5);
            double result = calc.calculate(candles);

            assertEquals(0.0, result, 0.001);
        }
    }
}
