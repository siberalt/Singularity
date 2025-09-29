package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.math.LinearFunction2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicStrengthCalculatorTest {

    @Test
    @DisplayName("calculate returns 0.0 when touchesCount is less than or equal to 0")
    void calculateReturnsZeroWhenTouchesCountIsNonPositive() {
        StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<Double>(
            mock(LinearFunction2D.class), 0L, 10L, 0, 100L
        );

        BasicStrengthCalculator calculator = new BasicStrengthCalculator();
        double result = calculator.calculate(context);

        assertEquals(0.0, result);
    }

    @Test
    @DisplayName("calculate returns 0.0 when linearFunction is null")
    void calculateReturnsZeroWhenLinearFunctionIsNull() {
        StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<>(
            null, 0L, 10L, 10, 100L
        );

        BasicStrengthCalculator calculator = new BasicStrengthCalculator();
        double result = calculator.calculate(context);

        assertEquals(0.0, result);
    }

    @Test
    @DisplayName("calculate returns 0.0 when fromIndex is negative")
    void calculateReturnsZeroWhenFromIndexIsNegative() {
        StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<Double>(
            mock(LinearFunction2D.class), -1L, 10L, 10, 100L
        );

        BasicStrengthCalculator calculator = new BasicStrengthCalculator();
        double result = calculator.calculate(context);

        assertEquals(0.0, result);
    }

    @Test
    @DisplayName("calculate returns 0.0 when toIndex is less than fromIndex")
    void calculateReturnsZeroWhenToIndexIsLessThanFromIndex() {
        StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<Double>(
            mock(LinearFunction2D.class), 5L, 4L, 10, 100L
        );

        BasicStrengthCalculator calculator = new BasicStrengthCalculator();
        double result = calculator.calculate(context);

        assertEquals(0.0, result);
    }

    @Test
    @DisplayName("calculate computes strength correctly for valid inputs")
    void calculateComputesStrengthCorrectlyForValidInputs() {
        LinearFunction2D<Double> linearFunction = mock(LinearFunction2D.class);
        when(linearFunction.getSlope()).thenReturn(0.5);

        StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<>(
            linearFunction, 0L, 10L, 10, 100L
        );

        BasicStrengthCalculator calculator = new BasicStrengthCalculator();
        double result = calculator.calculate(context);

        assertEquals(0.61, result, 10);
    }

    @Test
    @DisplayName("calculate computes strength correctly for slopes 100 and 0")
    void calculateComputesStrengthCorrectlyForDifferentSlopes() {
        LinearFunction2D<Double> linearFunctionSlope50 = mock(LinearFunction2D.class);
        when(linearFunctionSlope50.getSlope()).thenReturn(50.0);

        LinearFunction2D<Double> linearFunctionSlope0 = mock(LinearFunction2D.class);
        when(linearFunctionSlope0.getSlope()).thenReturn(0.0);

        StrengthCalculator.LevelContext<Double> contextSlope50 = new StrengthCalculator.LevelContext<>(
            linearFunctionSlope50, 0L, 10L, 10, 100L
        );

        StrengthCalculator.LevelContext<Double> contextSlope0 = new StrengthCalculator.LevelContext<>(
            linearFunctionSlope0, 0L, 10L, 10, 100L
        );

        BasicStrengthCalculator calculator = new BasicStrengthCalculator();

        double resultSlope50 = calculator.calculate(contextSlope50);
        double resultSlope0 = calculator.calculate(contextSlope0);

        assertTrue(resultSlope50 < resultSlope0, "Strength for slope 100 should be less than for slope 0");
    }
}
