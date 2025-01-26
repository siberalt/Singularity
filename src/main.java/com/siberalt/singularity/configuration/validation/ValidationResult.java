package com.siberalt.singularity.configuration.validation;

import java.util.Collection;
import java.util.HashSet;

public class ValidationResult {
    protected Collection<Error> errors = new HashSet<>();

    public ValidationResult add(Error error) {
        errors.add(error);

        return this;
    }

    public ValidationResult add(Collection<Error> errors) {
        this.errors.addAll(errors);

        return this;
    }

    public Collection<Error> getErrors() {
        return errors;
    }

    public boolean isValid(){
        return errors.isEmpty();
    }
}
