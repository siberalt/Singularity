package com.siberalt.singularity.configuration.exception;

import com.siberalt.singularity.configuration.validation.Error;
import com.siberalt.singularity.configuration.validation.ValidationResult;

public class InvalidConfigException extends RuntimeException {
    protected ValidationResult result;

    public InvalidConfigException(ValidationResult result) {
        this.result = result;
    }

    public ValidationResult getResult() {
        return result;
    }

    @Override
    public String getMessage() {
        StringBuilder builder = new StringBuilder("Invalid config. errors:\n");

        for (Error error : result.getErrors()) {
            builder
                    .append(error.getConfigPath())
                    .append(" : ")
                    .append(error.getMessage())
                    .append("\n");
        }

        return builder.toString();
    }
}
