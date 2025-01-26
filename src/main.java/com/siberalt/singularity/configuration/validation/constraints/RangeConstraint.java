package com.siberalt.singularity.configuration.validation.constraints;

public class RangeConstraint extends Constraint {
    protected Object[] rangeValues;

    public RangeConstraint(Object[] rangeValues) {
        this.rangeValues = rangeValues;
        setMessage("Value should be in a range of values {{ allowedValues }}, but got {{ value }}");
    }

    public Object[] getRangeValues() {
        return rangeValues;
    }

    public RangeConstraint setRangeValues(Object[] rangeValues) {
        this.rangeValues = rangeValues;
        return this;
    }
}
