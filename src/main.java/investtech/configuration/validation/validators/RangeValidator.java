package investtech.configuration.validation.validators;

import investtech.configuration.validation.Error;
import investtech.configuration.validation.constraints.Constraint;
import investtech.configuration.validation.constraints.RangeConstraint;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class RangeValidator implements ConstraintValidatorInterface {
    @Override
    public Collection<Error> validate(Object value, Constraint constraint) {
        if (!(constraint instanceof RangeConstraint) || value == null) {
            return null;
        }

        Object[] rangeValues = ((RangeConstraint) constraint).getRangeValues();

        if (!isInRange(value, rangeValues)) {
            String message = constraint.getMessage()
                    .replace("{{ allowedValues }}", allowedValuesToString(rangeValues))
                    .replace("{{ value }}", value.toString());

            return Collections.singletonList(new Error(message));
        }

        return null;
    }

    protected String allowedValuesToString(Object[] values) {
        return Arrays.stream(values)
                .map(Object::toString)
                .reduce((x, y) -> x + ", " + y)
                .orElse("");
    }

    protected boolean isInRange(Object value, Object[] rangeValues) {
        return Arrays.asList(rangeValues).contains(value);
    }
}
