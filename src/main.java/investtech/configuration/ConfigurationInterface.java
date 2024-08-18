package investtech.configuration;

public interface ConfigurationInterface {
    Object get(String configPath);

    boolean has(String configPath);

    String getFullConfigPath(String configPath);
}
