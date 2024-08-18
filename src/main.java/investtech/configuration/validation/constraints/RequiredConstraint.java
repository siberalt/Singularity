package investtech.configuration.validation.constraints;

public class RequiredConstraint extends Constraint {
    public RequiredConstraint() {
        setMessage("Config item should not be empty");
    }
}
