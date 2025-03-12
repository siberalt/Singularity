package com.siberalt.singularity.configuration;

import java.util.List;
import java.util.Map;

/**
 * Interface for configuration management.
 */
public interface ConfigInterface {
    /**
     * Retrieves a configuration value based on the given configuration path.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as an Object
     */
    Object get(String configPath);

    /**
     * Retrieves a configuration value as a List based on the given configuration path.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a List of Objects
     */
    List<Object> getAsList(String configPath);

    /**
     * Retrieves a configuration value as a Map based on the given configuration path.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a Map with String keys and Object values
     */
    Map<String, Object> getAsMap(String configPath);

    /**
     * Checks if a configuration exists at the specified path.
     *
     * @param configPath the path to check for the configuration
     * @return true if the configuration exists, false otherwise
     */
    boolean has(String configPath);
}
