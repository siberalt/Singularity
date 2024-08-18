package investtech.configuration.exception;

import investtech.configuration.validation.Error;
import investtech.configuration.validation.ValidationResult;
import investtech.configuration.validation.constraints.Constraint;

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
