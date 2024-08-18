package investtech.configuration.validation.validators;

import investtech.configuration.validation.Error;
import investtech.configuration.validation.constraints.Constraint;

import java.util.Collection;

public interface ConstraintValidatorInterface {
    Collection<Error> validate(Object value, Constraint constraint);
}
