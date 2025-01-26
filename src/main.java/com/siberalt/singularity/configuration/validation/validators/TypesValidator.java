package com.siberalt.singularity.configuration.validation.validators;

import com.siberalt.singularity.configuration.validation.constraints.Constraint;
import com.siberalt.singularity.configuration.validation.constraints.TypesConstraint;
import com.siberalt.singularity.configuration.validation.Error;
import com.siberalt.singularity.configuration.validation.ValueType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class TypesValidator implements ConstraintValidatorInterface {
    @Override
    public Collection<Error> validate(Object value, Constraint constraint) {
        if (!(constraint instanceof TypesConstraint) || value == null) {
            return null;
        }

        ValueType valueType = detectType(value);
        ValueType[] allowedTypes = ((TypesConstraint) constraint).getAllowedTypes();

        if (!isTypeAllowed(valueType, allowedTypes)) {
            String message = constraint.getMessage()
                    .replace("{{ actualType }}", value.getClass().getTypeName())
                    .replace("{{ expectedTypes }}", allowedTypesToString(allowedTypes));

            return Collections.singletonList(new Error(message));
        }

        return null;
    }

    protected String allowedTypesToString(ValueType[] valueTypes) {
        return Arrays.stream(valueTypes)
                .map(Enum::name)
                .reduce((x, y) -> x + ", " + y)
                .orElse("");
    }

    protected ValueType detectType(Object value) {
        ValueType valueType;

        if (value instanceof String) {
            valueType = ValueType.STRING;
        } else if (value instanceof Integer) {
            valueType = ValueType.INTEGER;
        } else if (value instanceof Iterable<?>) {
            valueType = ValueType.SET;
        } else if (value instanceof Boolean) {
            valueType = ValueType.BOOLEAN;
        } else if (value instanceof Double) {
            valueType = ValueType.DOUBLE;
        } else {
            valueType = ValueType.UNKNOWN;
        }

        return valueType;
    }

    protected boolean isTypeAllowed(ValueType valueType, ValueType[] allowedTypes) {
        return Arrays.asList(allowedTypes).contains(valueType);
    }
}
