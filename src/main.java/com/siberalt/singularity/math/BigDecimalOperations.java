package com.siberalt.singularity.math;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalOperations implements ArithmeticOperations<BigDecimal> {
    @Override
    public BigDecimal add(BigDecimal a, BigDecimal b) {
        return a.add(b);
    }

    @Override
    public BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return a.subtract(b);
    }

    @Override
    public BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return a.multiply(b);
    }

    @Override
    public BigDecimal divide(BigDecimal a, BigDecimal b) {
        return a.divide(b, 10, RoundingMode.DOWN); // Scale of 10 and rounding mode
    }

    @Override
    public BigDecimal fromDouble(double value) {
        return BigDecimal.valueOf(value);
    }

    @Override
    public BigDecimal fromLong(long value) {
        return BigDecimal.valueOf(value);
    }

    @Override
    public int compare(BigDecimal a, BigDecimal b) {
        return a.compareTo(b);
    }
}
