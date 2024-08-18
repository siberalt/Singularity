package investtech.configuration.validation.constraints;

public abstract class Constraint {
    protected String message;

    public Constraint setMessage(String message) {
        this.message = message;

        return this;
    }

    public String getMessage() {
        return message;
    }
}
