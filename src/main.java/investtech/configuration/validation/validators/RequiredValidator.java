package investtech.configuration.validation.validators;

import investtech.configuration.validation.Error;
import investtech.configuration.validation.constraints.Constraint;
import investtech.configuration.validation.constraints.RequiredConstraint;

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
