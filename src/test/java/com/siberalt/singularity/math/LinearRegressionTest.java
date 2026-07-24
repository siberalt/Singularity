package com.siberalt.singularity.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LinearRegression - Тесты линейной регрессии")
class LinearRegressionTest {

    @Nested
    @DisplayName("Проверка корректности вычислений")
    class CorrectnessTests {

        @Test
        @DisplayName("должен вернуть точную линейную зависимость y = 1 + 2x")
        void shouldReturnPerfectLinearData() {
            double[] x = {0, 1, 2, 3, 4};
            double[] y = {1, 3, 5, 7, 9};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(2.0, regression.getSlope(), 1e-10);
            assertEquals(1.0, regression.getIntercept(), 1e-10);
            assertEquals(1.0, regression.getR2(), 1e-10);
        }

        @Test
        @DisplayName("должен обработать горизонтальную линию y = 5")
        void shouldHandleHorizontalLine() {
            double[] x = {0, 1, 2, 3, 4};
            double[] y = {5, 5, 5, 5, 5};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(0.0, regression.getSlope(), 1e-10);
            assertEquals(5.0, regression.getIntercept(), 1e-10);
            assertEquals(0.0, regression.getR2(), 1e-10);
        }

        @Test
        @DisplayName("должен обработать отрицательный наклон y = 10 - 2x")
        void shouldHandleNegativeSlope() {
            double[] x = {0, 1, 2, 3, 4};
            double[] y = {10, 8, 6, 4, 2};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(-2.0, regression.getSlope(), 1e-10);
            assertEquals(10.0, regression.getIntercept(), 1e-10);
            assertEquals(1.0, regression.getR2(), 1e-10);
        }

        @Test
        @DisplayName("должен корректно прогнозировать значения")
        void shouldPredictCorrectly() {
            double[] x = {0, 1, 2, 3, 4};
            double[] y = {2, 4, 6, 8, 10};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(2.0, regression.predict(0), 1e-10);
            assertEquals(4.0, regression.predict(1), 1e-10);
            assertEquals(6.0, regression.predict(2), 1e-10);
            assertEquals(12.0, regression.predict(5), 1e-10);
        }
    }

    @Nested
    @DisplayName("Проверка обработки шумных данных")
    class NoisyDataTests {

        @Test
        @DisplayName("должен обработать реалистичные данные с шумом")
        void shouldHandleRealisticNoisyData() {
            double[] x = {0, 1, 2, 3, 4, 5};
            double[] y = {1.1, 2.9, 5.2, 6.8, 9.1, 10.9};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(2.0, regression.getSlope(), 0.05);
            assertEquals(1.0, regression.getIntercept(), 0.06);
            assertTrue(regression.getR2() > 0.99, "R² должен быть близок к 1");
        }

        @Test
        @DisplayName("должен вычислить R² = 1 для идеального соответствия")
        void shouldCalculateR2ForPerfectFit() {
            double[] x = {1, 2, 3, 4, 5};
            double[] y = {2, 4, 6, 8, 10};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(1.0, regression.getR2(), 1e-10);
        }

        @Test
        @DisplayName("должен вычислить R² для плохого соответствия")
        void shouldCalculateR2ForPoorFit() {
            double[] x = {1, 2, 3, 4, 5};
            double[] y = {1, 10, 2, 9, 3};

            LinearRegression regression = new LinearRegression(x, y);

            assertTrue(regression.getR2() >= 0, "R² должен быть >= 0");
            assertTrue(regression.getR2() < 1, "R² должен быть < 1 для шумных данных");
        }
    }

    @Nested
    @DisplayName("Проверка edge cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("должен обработать все точки с одинаковым x (вертикальная линия)")
        void shouldHandleVerticalLine() {
            double[] x = {3, 3, 3, 3, 3};
            double[] y = {1, 2, 3, 4, 5};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(0.0, regression.getSlope(), 1e-10);
            assertEquals(3.0, regression.getIntercept(), 1e-10);
        }

        @Test
        @DisplayName("должен обработать две точки с одинаковым x")
        void shouldHandleSameXValues() {
            double[] x = {1, 1};
            double[] y = {2, 3};

            LinearRegression regression = new LinearRegression(x, y);

            assertEquals(0.0, regression.getSlope(), 1e-10);
            assertEquals(2.5, regression.getIntercept(), 1e-10);
        }
    }

    @Nested
    @DisplayName("Проверка обработки ошибок ввода")
    class ErrorHandlingTests {

        @Test
        @DisplayName("должен выбросить исключение для массивов разной длины")
        void shouldThrowExceptionForMismatchedLengths() {
            double[] x = {0, 1, 2, 3};
            double[] y = {1, 2, 3};

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new LinearRegression(x, y);
            });

            assertTrue(exception.getMessage().contains("одинаковую длину"));
        }

        @Test
        @DisplayName("должен выбросить исключение для менее чем 2 точек")
        void shouldThrowExceptionForLessThanTwoPoints() {
            double[] x = {1};
            double[] y = {2};

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new LinearRegression(x, y);
            });

            assertTrue(exception.getMessage().contains("минимум 2 элемента"));
        }
    }
}
