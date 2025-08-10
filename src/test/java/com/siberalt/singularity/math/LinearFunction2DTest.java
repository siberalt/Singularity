package com.siberalt.singularity.math;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LinearFunction2DTest {

    @Test
    void calculatesCorrectYValueForGivenX() {
        ArithmeticOperations<BigDecimal> operations = mock(ArithmeticOperations.class);
        when(operations.multiply(new BigDecimal("2"), new BigDecimal("3"))).thenReturn(new BigDecimal("6"));
        when(operations.add(new BigDecimal("6"), new BigDecimal("1"))).thenReturn(new BigDecimal("7"));

        LinearFunction2D<BigDecimal> function = new LinearFunction2D<>(new BigDecimal("2"), new BigDecimal("1"), operations);
        assertEquals(new BigDecimal("7"), function.calculate(new BigDecimal("3")));
    }

    @Test
    void identifiesPointWithinNeighbourhood() {
        ArithmeticOperations<BigDecimal> operations = mock(ArithmeticOperations.class);
        when(operations.multiply(new BigDecimal("5"), new BigDecimal("1.1"))).thenReturn(new BigDecimal("5.5"));
        when(operations.multiply(new BigDecimal("5"), new BigDecimal("0.9"))).thenReturn(new BigDecimal("4.5"));
        when(operations.compare(new BigDecimal("5.2"), new BigDecimal("5.5"))).thenReturn(-1);
        when(operations.compare(new BigDecimal("5.2"), new BigDecimal("4.5"))).thenReturn(1);

        LinearFunction2D<BigDecimal> function = new LinearFunction2D<>(new BigDecimal("1"), new BigDecimal("5"), operations);
        assertTrue(function.isInNeighbourhood(new BigDecimal("1"), new BigDecimal("5.2"), 0.1));
    }

    @Test
    void createsFunctionFromTwoPoints() {
        ArithmeticOperations<BigDecimal> operations = ArithmeticOperations.BIG_DECIMAL;

        LinearFunction2D<BigDecimal> function = LinearFunction2D.fromPoints(
            new Point2D<>(new BigDecimal("1"), new BigDecimal("2")),
            new Point2D<>(new BigDecimal("2"), new BigDecimal("4")),
            operations
        );

        assertEquals(0, function.calculate(new BigDecimal("1")).compareTo(new BigDecimal("2")));
        assertEquals(0, function.calculate(new BigDecimal("2")).compareTo(new BigDecimal("4")));
        assertEquals(0, function.calculate(new BigDecimal("3")).compareTo(new BigDecimal("6")));
        assertEquals(0, function.calculate(new BigDecimal("4")).compareTo(new BigDecimal("8")));
    }

    @Test
    void throwsExceptionWhenPointsHaveSameXCoordinate() {
        ArithmeticOperations<BigDecimal> operations = mock(ArithmeticOperations.class);
        when(operations.compare(new BigDecimal("1"), new BigDecimal("1"))).thenReturn(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> LinearFunction2D.fromPoints(
                new Point2D<>(new BigDecimal("1"), new BigDecimal("2")),
                new Point2D<>(new BigDecimal("1"), new BigDecimal("4")),
                operations
            ));
        assertEquals("Points must have different x-coordinates to define a linear function.", exception.getMessage());
    }
}
