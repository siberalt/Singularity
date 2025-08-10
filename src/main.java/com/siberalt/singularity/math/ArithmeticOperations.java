package com.siberalt.singularity.math;

import java.math.BigDecimal;

public interface ArithmeticOperations<T extends Number> {
    ArithmeticOperations<BigDecimal> BIG_DECIMAL = new BigDecimalOperations();
    ArithmeticOperations<Double> DOUBLE = new DoubleOperations();

    T add(T a, T b);

    T subtract(T a, T b);

    T multiply(T a, T b);

    T divide(T a, T b);

    T fromDouble(double value);

    T fromLong(long value);

    int compare(T a, T b);
}
