package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeightedVolatilityCalculatorTest {

    // Простая заглушка свечи
    private Candle candle(double high, double low, double close) {
        return Candle.of(TimePoint.NULL, 0, 0, high, low, close);
    }

    private final List<Candle> sampleCandles = List.of(
        candle(100.0, 90.0, 95.0),
        candle(105.0, 95.0, 102.0),
        candle(110.0, 100.0, 108.0)
    );

    @Nested
    @DisplayName("Базовые вычисления")
    class BasicCalculations {

        @Test
        @DisplayName("Должен корректно вычислять взвешенную сумму")
        void shouldCalculateWeightedSumCorrectly() {
            VolatilityCalculator calc1 = mock(VolatilityCalculator.class);
            VolatilityCalculator calc2 = mock(VolatilityCalculator.class);

            when(calc1.calculate(sampleCandles)).thenReturn(10.0);
            when(calc2.calculate(sampleCandles)).thenReturn(5.0);

            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(calc1, 0.7)
                .add(calc2, 0.3);

            double result = calculator.calculate(sampleCandles);

            assertEquals(0.7 * 10.0 + 0.3 * 5.0, result, 0.001);
            verify(calc1).calculate(sampleCandles);
            verify(calc2).calculate(sampleCandles);
        }

        @Test
        @DisplayName("Порядок добавления не должен влиять на результат")
        void orderOfAdditionShouldNotMatter() {
            VolatilityCalculator calcA = c -> 2.0;
            VolatilityCalculator calcB = c -> 4.0;

            WeightedVolatilityCalculator forward = new WeightedVolatilityCalculator()
                .add(calcA, 0.4)
                .add(calcB, 0.6);

            WeightedVolatilityCalculator backward = new WeightedVolatilityCalculator()
                .add(calcB, 0.6)
                .add(calcA, 0.4);

            assertEquals(forward.calculate(null), backward.calculate(null), 0.001);
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При отсутствии компонентов должен вернуть 0.0")
        void shouldReturnZeroWhenNoComponents() {
            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator();
            double result = calculator.calculate(sampleCandles);
            assertEquals(0.0, result, 0.001);
        }

        @Test
        @DisplayName("При null списке свечей — делегирует дальше без ошибок")
        void shouldHandleNullCandlesGracefully() {
            VolatilityCalculator mockCalc = mock(VolatilityCalculator.class);
            when(mockCalc.calculate(null)).thenReturn(3.0);

            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(mockCalc, 1.0);

            double result = calculator.calculate(null);

            assertEquals(3.0, result, 0.001);
            verify(mockCalc).calculate(null);
        }

        @Test
        @DisplayName("При пустом списке свечей — делегирует дальше")
        void shouldHandleEmptyCandles() {
            VolatilityCalculator mockCalc = mock(VolatilityCalculator.class);
            when(mockCalc.calculate(Collections.emptyList())).thenReturn(1.5);

            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(mockCalc, 1.0);

            double result = calculator.calculate(Collections.emptyList());

            assertEquals(1.5, result, 0.001);
        }
    }

    @Nested
    @DisplayName("Однокомпонентные и крайние веса")
    class SingleAndExtremeWeights {

        @Test
        @DisplayName("Один компонент с весом 1.0 — должен вернуть его значение")
        void singleComponentWithWeightOneShouldReturnItsValue() {
            VolatilityCalculator mockCalc = c -> 7.77;

            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(mockCalc, 1.0);

            double result = calculator.calculate(sampleCandles);

            assertEquals(7.77, result, 0.001);
        }

        @Test
        @DisplayName("Компонент с нулевым весом — не должен влиять на результат")
        void componentWithZeroWeightShouldBeIgnored() {
            VolatilityCalculator active = c -> 10.0;
            VolatilityCalculator ignored = c -> 999.0; // будет проигнорирован

            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(active, 1.0)
                .add(ignored, 0.0);

            double result = calculator.calculate(sampleCandles);

            assertEquals(10.0, result, 0.001);
        }

        @Test
        @DisplayName("Множество компонентов с малыми весами — корректно суммируются")
        void multipleSmallWeightsShouldSumCorrectly() {
            VolatilityCalculator calc1 = c -> 2.0;
            VolatilityCalculator calc2 = c -> 4.0;
            VolatilityCalculator calc3 = c -> 6.0;

            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(calc1, 0.1)
                .add(calc2, 0.3)
                .add(calc3, 0.6);

            double expected = 0.1 * 2.0 + 0.3 * 4.0 + 0.6 * 6.0;
            double actual = calculator.calculate(sampleCandles);

            assertEquals(expected, actual, 0.001);
        }
    }

    @Nested
    @DisplayName("Надёжность и безопасность")
    class Reliability {

        @Test
        @DisplayName("Добавление null калькулятора должно вызвать исключение")
        void addingNullCalculatorShouldThrowException() {
            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator();

            assertThrows(NullPointerException.class, () -> calculator.add(null, 0.5));
        }

        @Test
        @DisplayName("Вес может быть любым числом (включая отрицательный или >1)")
        void weightsCanBeAnyDouble() {
            VolatilityCalculator calc = c -> 1.0;

            // Хотя веса не нормализуются — это ответственность пользователя
            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator()
                .add(calc, -0.5)  // допустимо
                .add(calc, 2.0); // допустимо

            double result = calculator.calculate(sampleCandles);
            assertEquals(-0.5 + 2.0, result, 0.001);
        }

        @Test
        @DisplayName("Fluent API: add возвращает this")
        void addShouldReturnThisForFluentChaining() {
            WeightedVolatilityCalculator calculator = new WeightedVolatilityCalculator();
            assertSame(calculator, calculator.add(c -> 1.0, 0.5));
        }
    }

    @Nested
    @DisplayName("Интеграционные примеры")
    class IntegrationExamples {

        @Test
        @DisplayName("Можно комбинировать разные типы калькуляторов")
        void canCombineDifferentCalculators() {
            VolatilityCalculator atr = c -> 3.0;
            VolatilityCalculator stdDev = c -> 4.0;
            VolatilityCalculator range = c -> 5.0;

            WeightedVolatilityCalculator ensemble = new WeightedVolatilityCalculator()
                .add(atr, 0.5)
                .add(stdDev, 0.3)
                .add(range, 0.2);

            double result = ensemble.calculate(sampleCandles);
            double expected = 0.5 * 3.0 + 0.3 * 4.0 + 0.2 * 5.0;

            assertEquals(expected, result, 0.001);
        }
    }
}
