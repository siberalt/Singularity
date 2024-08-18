package investtech.configuration.validation;

import investtech.configuration.ConfigurationInterface;
import investtech.configuration.exception.InvalidConfigException;
import investtech.configuration.validation.constraints.Constraint;
import investtech.configuration.validation.validators.ConstraintValidatorInterface;

import java.util.*;

public class ValidatorManager {
    Map<Class<? extends Constraint>, ConstraintValidatorInterface> constraintValidators = new HashMap<>();

    public ValidatorManager add(
            Class<? extends Constraint> constraintClass,
            ConstraintValidatorInterface validator
    ) {
        this.constraintValidators.put(constraintClass, validator);

        return this;
    }

    public ConstraintValidatorInterface get(Class<? extends Constraint> constraintClass) {
        return constraintValidators.get(constraintClass);
    }

    public ValidatorManager clear() {
        constraintValidators.clear();

        return this;
    }

    public void validateWithException(ConfigurationInterface configuration, ConstraintsAggregate aggregate) {
        ValidationResult result = validate(configuration, aggregate);

        if (!result.isValid()) {
            throw new InvalidConfigException(result);
        }
    }

    public ValidationResult validate(ConfigurationInterface configuration, ConstraintsAggregate aggregate) {
        ValidationResult result = new ValidationResult();
        Collection<Error> errors = new HashSet<>();

        for (Map.Entry<String, Collection<Constraint>> pathConstraints : aggregate.getAllPathsConstraints()) {
            String configPath = pathConstraints.getKey();
            Object value = configuration.get(configPath);
            String fullConfigPath = configuration.getFullConfigPath(configPath);

            for (Constraint constraint : pathConstraints.getValue()) {
                Collection<Error> validatorErrors = get(constraint.getClass()).validate(value, constraint);

                if (null != validatorErrors) {
                    for (Error error: validatorErrors) {
                        error.setConfigPath(fullConfigPath);
                    }

                    errors.addAll(validatorErrors);
                }
            }
        }


        return result.add(errors);
    }
}
