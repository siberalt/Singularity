package investtech.configuration.validation.constraints;

import investtech.configuration.validation.ValueType;

public class TypesConstraint extends Constraint {
    protected ValueType[] allowedTypes;

    public TypesConstraint(ValueType[] allowedTypes) {
        this.allowedTypes = allowedTypes;
        setMessage("Value type is invalid. Got {{ actualType }}, Expected: {{ expectedTypes }}");
    }

    public ValueType[] getAllowedTypes() {
        return allowedTypes;
    }

    public TypesConstraint setAllowedTypes(ValueType[] allowedTypes) {
        this.allowedTypes = allowedTypes;
        return this;
    }
}
