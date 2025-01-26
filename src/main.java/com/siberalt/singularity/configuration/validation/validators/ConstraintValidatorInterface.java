package com.siberalt.singularity.configuration.validation.validators;

import com.siberalt.singularity.configuration.validation.constraints.Constraint;
import com.siberalt.singularity.configuration.validation.Error;

import java.util.Collection;

public interface ConstraintValidatorInterface {
    Collection<Error> validate(Object value, Constraint constraint);
}
