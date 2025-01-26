package com.siberalt.singularity.broker.contract.value.quotation;

import com.siberalt.singularity.simulation.shared.market.candle.ComparisonOperator;

import java.math.BigDecimal;
import java.math.MathContext;

public class Quotation {
    public static final Quotation ZERO = Quotation.of(BigDecimal.ZERO);

    protected long units;
    protected int nano;

    protected BigDecimal value;

    public long getUnits() {
        return units;
    }

    public Quotation setUnits(long units) {
        this.units = units;
        return this;
    }

    public int getNano() {
        return nano;
    }

    public Quotation setNano(int nano) {
        this.nano = nano;
        return this;
    }

    // region Arithmetic operators
    public Quotation add(Quotation quotation) {
        return Quotation.of(toBigDecimal().add(quotation.toBigDecimal()));
    }

    public Quotation add(double value) {
        return Quotation.of(toBigDecimal().add(BigDecimal.valueOf(value)));
    }

    public Quotation add(int value) {
        return Quotation.of(toBigDecimal().add(BigDecimal.valueOf(value)));
    }

    public Quotation add(BigDecimal value) {
        return Quotation.of(toBigDecimal().add(value));
    }

    public Quotation subtract(Quotation quotation) {
        return Quotation.of(toBigDecimal().subtract(quotation.toBigDecimal()));
    }

    public Quotation multiply(int multiplyBy) {
        return Quotation.of(toBigDecimal().multiply(BigDecimal.valueOf(multiplyBy)));
    }

    public Quotation multiply(double multiplyBy) {
        return Quotation.of(toBigDecimal().multiply(BigDecimal.valueOf(multiplyBy)));
    }

    public Quotation multiply(Quotation quotation) {
        return Quotation.of(toBigDecimal().multiply(quotation.toBigDecimal()));
    }

    public Quotation multiply(BigDecimal multiplier) {
        return Quotation.of(toBigDecimal().multiply(multiplier));
    }

    public Quotation divide(long divideBy) {
        return divide(BigDecimal.valueOf(divideBy));
    }

    public Quotation divide(int divideBy) {
        return divide(BigDecimal.valueOf(divideBy));
    }

    public Quotation divide(Quotation divideBy) {
        return divide(divideBy.toBigDecimal());
    }

    public Quotation divide(BigDecimal divideBy) {
        return Quotation.of(toBigDecimal().divide(divideBy, MathContext.DECIMAL128));
    }
    // endregion Arithmetic operators

    // region logic operators
    public boolean isMore(Quotation value) {
        return isMore(value.toBigDecimal());
    }

    public boolean isMore(BigDecimal value) {
        return toBigDecimal().compareTo(value) > 0;
    }

    public boolean isMoreOrEqual(BigDecimal value) {
        return toBigDecimal().compareTo(value) >= 0;
    }

    public boolean isMoreOrEqual(Quotation value) {
        return isMoreOrEqual(value.toBigDecimal());
    }

    public boolean isEqual(Quotation value) {
        return isEqual(value.toBigDecimal());
    }

    public boolean isEqual(BigDecimal value) {
        return toBigDecimal().compareTo(value) == 0;
    }

    public boolean isLess(Quotation value) {
        return isLess(value.toBigDecimal());
    }

    public boolean isLess(BigDecimal value) {
        return toBigDecimal().compareTo(value) < 0;
    }

    public boolean isLessOrEqual(Quotation value) {
        return isLessOrEqual(value.toBigDecimal());
    }

    public boolean isLessOrEqual(BigDecimal value) {
        return toBigDecimal().compareTo(value) <= 0;
    }

    // endregion logic operators

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Quotation)) {
            return false;
        }

        return this.isEqual((Quotation) obj);
    }

    @Override
    public String toString() {
        return toBigDecimal().toString();
    }

    public BigDecimal toBigDecimal() {
        if (null == value) {
            value = units == 0 && nano == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
        }

        return value;
    }

    public boolean compare(Quotation quotationB, ComparisonOperator operator) {
        return compare(this, quotationB, operator);
    }

    public static boolean compare(Quotation quotationA, Quotation quotationB, ComparisonOperator operator) {
        return switch (operator) {
            case EQUAL -> quotationA.isEqual(quotationB);
            case LESS -> quotationA.isLess(quotationB);
            case LESS_OR_EQUAL -> quotationA.isLessOrEqual(quotationB);
            case MORE -> quotationA.isMore(quotationB);
            case MORE_OR_EQUAL -> quotationA.isMoreOrEqual(quotationB);
            case NOT_EQUAL -> !quotationA.isEqual(quotationB);
        };
    }

    public static Quotation of(long units, int nano) {
        return new Quotation().setNano(nano).setUnits(units);
    }

    public static Quotation of(long value) {
        return Quotation.of(BigDecimal.valueOf(value));
    }

    public static Quotation of(String value) {
        return Quotation.of(BigDecimal.valueOf(Double.parseDouble(value)));
    }

    public static Quotation of(Double value) {
        return Quotation.of(BigDecimal.valueOf(value));
    }

    public static Quotation of(BigDecimal value) {
        return new Quotation()
                .setUnits(value.longValue())
                .setNano(value.remainder(BigDecimal.ONE).multiply(BigDecimal.valueOf(1_000_000_000)).intValue());
    }
}
