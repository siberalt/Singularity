package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ATRVolatilityCalculatorTest {

    private final ATRVolatilityCalculator calculator = new ATRVolatilityCalculator(14);

    // Простая заглушка Candle
    private Candle candle(double high, double low, double close) {
        return Candle.of(TimePoint.NULL, 0, 0, high, low, close);
    }

    @Nested
    @DisplayName("Базовые кейсы")
    class BasicCases {

        @Test
        @DisplayName("Должен вернуть 0 при пустом списке")
        void shouldReturnZeroOnEmptyList() {
            double atr = calculator.calculate(Collections.emptyList());
            assertEquals(0.0, atr, 0.001);
        }

        @Test
        @DisplayName("Должен вернуть 0 при одной свече")
        void shouldReturnZeroOnSingleCandle() {
            List<Candle> candles = Collections.singletonList(candle(100.0, 90.0, 95.0));
            double atr = calculator.calculate(candles);
            assertEquals(0.0, atr, 0.001);
        }

        @Test
        @DisplayName("Должен вернуть 0 при двух свечах, но период > 2")
        void shouldReturnZeroIfNotEnoughData() {
            List<Candle> candles = Arrays.asList(
                candle(100.0, 90.0, 95.0),
                candle(102.0, 91.0, 101.0)
            );
            double atr = calculator.calculate(candles); // period = 14 → недостаточно данных
            assertEquals(0.0, atr, 0.001);
        }
    }

    @Nested
    @DisplayName("Расчёт ATR")
    class ATRCalculation {

        @Test
        @DisplayName("Должен корректно рассчитать ATR по первым 14 свечам (простое среднее)")
        void shouldCalculateFirstATRAsSimpleAverage() {
            // Создадим 15 свечей с одинаковым True Range = 10.0
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                // TR = High - Low = 10.0
                candles.add(candle(100.0 + i, 90.0 + i, 95.0 + i));
            }

            double atr = calculator.calculate(candles);
            assertEquals(10.0, atr, 0.001, "Первый ATR должен быть средним от 14 TR");
        }

        @Test
        @DisplayName("Должен перейти на EMA после первого значения")
        void shouldSwitchToEMACalculationAfterFirst() {
            // 14 свечей с TR = 10.0 → первый ATR = 10.0
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                candles.add(candle(100.0, 90.0, 95.0));
            }

            // Добавим 15-ю свечу с TR = 20.0
            candles.add(candle(110.0, 90.0, 100.0)); // TR = 20.0

            double atr = calculator.calculate(candles);
            double expected = (10.0 * 13 + 20.0) / 14; // EMA-like: (prev * 13 + current) / 14
            assertEquals(expected, atr, 0.001);
        }

        @Test
        @DisplayName("Ручной расчёт ATR совпадает с ожидаемым")
        void manualCalculationMatchesExpected() {
            List<Candle> candles = Arrays.asList(
                candle(105.0, 100.0, 102.0), // TR = 5.0
                candle(106.0, 101.0, 105.0), // TR = 5.0
                candle(108.0, 102.0, 107.0), // TR = 6.0
                candle(110.0, 104.0, 109.0), // TR = 6.0
                candle(112.0, 106.0, 111.0), // TR = 6.0
                candle(114.0, 108.0, 113.0), // TR = 6.0
                candle(116.0, 110.0, 115.0), // TR = 6.0
                candle(118.0, 112.0, 117.0), // TR = 6.0
                candle(120.0, 114.0, 119.0), // TR = 6.0
                candle(122.0, 116.0, 121.0), // TR = 6.0
                candle(124.0, 118.0, 123.0), // TR = 6.0
                candle(126.0, 120.0, 125.0), // TR = 6.0
                candle(128.0, 122.0, 127.0), // TR = 6.0
                candle(130.0, 124.0, 129.0), // TR = 6.0 → Simple Avg = 5.7857...?
                candle(135.0, 125.0, 134.0)  // TR = 10.0
            );

            // Первые 14: среднее TR
            double sum = 0.0;
            for (int i = 1; i < 15; i++) {
                Candle curr = candles.get(i);
                Candle prev = candles.get(i - 1);
                sum += calculateTrueRange(curr, prev); // первые 14 TR
            }

            double expected = sum / 14; // ← корректно: 14 значений

            double actual = calculator.calculate(candles);
            assertEquals(expected, actual, 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null списке — возвращает 0")
        void shouldReturnZeroOnNull() {
            double atr = calculator.calculate(null);
            assertEquals(0.0, atr, 0.001);
        }

        @Test
        @DisplayName("Все свечи с TR = 0 → ATR = 0")
        void allZeroRangeShouldGiveZeroATR() {
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                candles.add(candle(100.0, 100.0, 100.0)); // no range
            }
            double atr = calculator.calculate(candles);
            assertEquals(0.0, atr, 0.001);
        }

        @Test
        @DisplayName("Большой гэп вверх — правильно учитывается TR")
        void largeGapUpShouldBeIncludedInTR() {
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < 13; i++) {
                candles.add(candle(100.0, 90.0, 95.0)); // TR = 10.0
            }
            Candle prev = candle(100.0, 90.0, 95.0);
            Candle gapUp = candle(120.0, 110.0, 115.0); // TR = 10.0, но Close-to-PrevClose = 20.0
            candles.add(prev);
            candles.add(gapUp);

            // True Range = max(high - low, |high - prevClose|, |low - prevClose|)
            double range1 = gapUp.getHighAsDouble() - gapUp.getLowAsDouble(); // 10.0
            double range2 = Math.abs(gapUp.getHighAsDouble() - prev.getCloseAsDouble()); // |120 - 95| = 25.0
            double range3 = Math.abs(gapUp.getLowAsDouble() - prev.getCloseAsDouble()); // |110 - 95| = 15.0

            double tr = Math.max(range1, Math.max(range2, range3)); // → max(10, 25, 15) = 25.0

            // First 14: 13×10 + 25 = 155 → avg = 11.142
            double expectedFirstATR = (13 * 10.0 + 25.0) / 14;

            double atr = calculator.calculate(candles);
            assertEquals(expectedFirstATR, atr, 0.001);
        }
    }

    @Nested
    @DisplayName("Настройка периода")
    class PeriodConfiguration {

        @Test
        @DisplayName("Можно задать кастомный период")
        void shouldSupportCustomPeriod() {
            ATRVolatilityCalculator custom = new ATRVolatilityCalculator(5);
            List<Candle> candles = Arrays.asList(
                candle(105, 100, 0),
                candle(106, 101, 0),
                candle(107, 102, 0),
                candle(108, 103, 0),
                candle(109, 104, 0),
                candle(110, 105, 0) // 6-я свеча → достаточно для периода 5
            );

            double atr = custom.calculate(candles);
            assertTrue(atr > 0);
        }
    }


    private double calculateTrueRange(Candle current, Candle previous) {
        double highLow = current.getHighAsDouble() - current.getLowAsDouble();
        double highPrevClose = Math.abs(current.getHighAsDouble() - previous.getCloseAsDouble());
        double lowPrevClose = Math.abs(current.getLowAsDouble() - previous.getCloseAsDouble());

        return Math.max(highLow, Math.max(highPrevClose, lowPrevClose));
    }

}
