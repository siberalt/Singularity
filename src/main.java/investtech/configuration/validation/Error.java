package investtech.configuration.validation;

public class Error {
    protected String configPath;

    protected String message;

    public Error(String message) {
        this.message = message;
    }

    public String getConfigPath() {
        return configPath;
    }

    public Error setConfigPath(String configItem) {
        this.configPath = configItem;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public Error setMessage(String message) {
        this.message = message;
        return this;
    }
}
