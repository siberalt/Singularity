package com.siberalt.singularity.math;

public class DoubleOperations implements ArithmeticOperations<Double> {
    @Override
    public Double add(Double a, Double b) {
        return a + b;
    }

    @Override
    public Double subtract(Double a, Double b) {
        return a - b;
    }

    @Override
    public Double multiply(Double a, Double b) {
        return a * b;
    }

    @Override
    public Double divide(Double a, Double b) {
        return a / b; // No scale or rounding for doubles
    }

    @Override
    public Double fromDouble(double value) {
        return value;
    }

    @Override
    public Double fromLong(long value) {
        return (double) value;
    }

    @Override
    public int compare(Double a, Double b) {
        return Double.compare(a, b);
    }
}
