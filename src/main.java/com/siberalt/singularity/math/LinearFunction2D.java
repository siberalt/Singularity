package com.siberalt.singularity.math;

import java.util.function.Function;

public class LinearFunction2D<T extends Number> implements Function<T, T> {
    private final T slope; // The slope of the line (m)
    private final T intercept; // The y-intercept of the line (b)

    private final ArithmeticOperations<T> operations;

    public LinearFunction2D(T slope, T intercept, ArithmeticOperations<T> operations) {
        if (slope == null || intercept == null || operations == null) {
            throw new IllegalArgumentException("Slope, intercept, and operations cannot be null.");
        }
        this.slope = slope;
        this.intercept = intercept;
        this.operations = operations;
    }

    public T getSlope() {
        return slope;
    }

    public T getIntercept() {
        return intercept;
    }

    public T calculate(T x) {
        return operations.add(operations.multiply(slope, x), intercept);
    }

    public boolean isInNeighbourhood(T x, T y, double neighbourhoodRatio) {
        T expectedY = calculate(x);
        T upperBound = operations.multiply(expectedY, operations.fromDouble(1 + neighbourhoodRatio));
        T lowerBound = operations.multiply(expectedY, operations.fromDouble(1 - neighbourhoodRatio));
        return operations.compare(y, upperBound) <= 0 && operations.compare(y, lowerBound) >= 0;
    }

    public static <T extends Number> LinearFunction2D<T> fromPoints(Point2D<T> p1, Point2D<T> p2, ArithmeticOperations<T> operations) {
        if (operations.compare(p1.x(), p2.x()) == 0) {
            throw new IllegalArgumentException("Points must have different x-coordinates to define a linear function.");
        }

        T slope = operations.divide(operations.subtract(p2.y(), p1.y()), operations.subtract(p2.x(), p1.x()));
        T intercept = operations.subtract(p1.y(), operations.multiply(slope, p1.x()));

        return new LinearFunction2D<>(slope, intercept, operations);
    }

    @Override
    public T apply(T t) {
        return calculate(t);
    }
}
