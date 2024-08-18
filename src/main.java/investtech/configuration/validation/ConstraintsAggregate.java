package investtech.configuration.validation;

import investtech.configuration.validation.constraints.Constraint;
import investtech.configuration.validation.constraints.RequiredConstraint;
import investtech.configuration.validation.constraints.TypesConstraint;

import java.util.*;

public class ConstraintsAggregate {
    Map<String, Collection<Constraint>> constraints = new HashMap<>();

    public Collection<Constraint> getPathConstraints(String configPath) {
        return this.constraints.computeIfAbsent(configPath, k -> new HashSet<>());
    }

    public ConstraintsAggregate add(String configPath, Constraint constraint) {
        getPathConstraints(configPath).add(constraint);

        return this;
    }

    public ConstraintsAggregate add(String configPath, Collection<Constraint> constraints) {
        getPathConstraints(configPath).addAll(constraints);

        return this;
    }

    public Set<Map.Entry<String, Collection<Constraint>>> getAllPathsConstraints() {
        return constraints.entrySet();
    }

    public static Collection<Constraint> of(boolean required) {
        return of(required, new ValueType[]{ValueType.STRING});
    }

    public static Collection<Constraint> of(ValueType valueType) {
        return of(false, valueType);
    }

    public static Collection<Constraint> of(boolean required, ValueType allowedType) {
        return of(required, new ValueType[]{allowedType});
    }

    public static Collection<Constraint> of(boolean required, ValueType[] allowedTypes) {
        Collection<Constraint> constraints = new HashSet<>();

        if (required) {
            constraints.add(new RequiredConstraint());
        }

        constraints.add(new TypesConstraint(allowedTypes));

        return constraints;
    }
}
