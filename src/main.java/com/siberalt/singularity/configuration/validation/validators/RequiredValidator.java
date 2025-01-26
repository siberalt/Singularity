package com.siberalt.singularity.configuration.validation.validators;

import com.siberalt.singularity.configuration.validation.constraints.Constraint;
import com.siberalt.singularity.configuration.validation.constraints.RequiredConstraint;
import com.siberalt.singularity.configuration.validation.Error;

import java.util.*;

public class RequiredValidator implements ConstraintValidatorInterface {
    @Override
    public Collection<Error> validate(Object value, Constraint constraint) {
        if (!(constraint instanceof RequiredConstraint)) {
            return null;
        }

        if (null == value || (value instanceof String && ((String) value).isEmpty())) {
            return Collections.singletonList(new Error(constraint.getMessage()));
        }

        return null;
    }
}
