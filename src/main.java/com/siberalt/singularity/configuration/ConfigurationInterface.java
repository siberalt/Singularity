package com.siberalt.singularity.configuration;

/**
 * Interface for configuration management.
 */
public interface ConfigurationInterface {
    /**
     * Retrieves a configuration value based on the given configuration path.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as an Object
     */
    Object get(String configPath);

    /**
     * Checks if a configuration exists at the specified path.
     *
     * @param configPath the path to check for the configuration
     * @return true if the configuration exists, false otherwise
     */
    boolean has(String configPath);

    /**
     * Returns the full path of the configuration based on the given path.
     *
     * @param configPath the relative configuration path
     * @return the full configuration path as a String
     */
    String getFullConfigPath(String configPath);

    /**
     * Retrieves a configuration value as a ConfigurationInterface.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a ConfigurationInterface
     */
    default ConfigurationInterface getAsConfiguration(String configPath) {
        return (ConfigurationInterface) get(configPath);
    }

    /**
     * Retrieves a configuration value as a String.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a String
     */
    default String getAsString(String configPath) {
        Object value = get(configPath);
        return value != null ? value.toString() : null;
    }

    /**
     * Retrieves a configuration value as an integer.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as an integer
     * @throws NumberFormatException if the value cannot be converted to an integer
     */
    default int getAsInt(String configPath) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * Retrieves a configuration value as a boolean.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a boolean
     */
    default boolean getAsBoolean(String configPath) {
        Object value = get(configPath);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Retrieves a configuration value as a long.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a long
     */
    default long getAsLong(String configPath) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /**
     * Retrieves a configuration value as a double.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a double
     */
    default double getAsDouble(String configPath) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    /**
     * Retrieves a configuration value as a float.
     *
     * @param configPath the path to the configuration value
     * @return the configuration value as a float
     */
    default float getAsFloat(String configPath) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return Float.parseFloat(value.toString());
    }

    /**
     * Retrieves a configuration value as an enum of the specified type.
     *
     * @param configPath the path to the configuration value
     * @param enumType the class of the enum type
     * @param <T> the type of the enum
     * @return the configuration value as an enum of the specified type
     */
    default <T extends Enum<T>> T getAsEnum(String configPath, Class<T> enumType) {
        Object value = get(configPath);
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        return Enum.valueOf(enumType, value.toString());
    }
}
