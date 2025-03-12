package com.siberalt.singularity.service;

import com.siberalt.singularity.configuration.ConfigInterface;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public class ConfigFacade {
    private final ConfigInterface config;

    public ConfigFacade(ConfigInterface config) {
        this.config = config;
    }

    public Object get(String configPath) {
        return config.get(configPath);
    }

    public List<?> getAsList(String configPath) {
        return config.getAsList(configPath);
    }

    public Map<?, ?> getAsMap(String configPath) {
        return config.getAsMap(configPath);
    }

    public boolean has(String configPath) {
        return config.has(configPath);
    }

    public List<?> getAsList(String configPath, List<Object> defaultValue) {
        List<?> value = getAsList(configPath);
        return value != null ? value : defaultValue;
    }

    public Map<?, ?> getAsMap(String configPath, Map<String, Object> defaultValue) {
        Map<?, ?> value = getAsMap(configPath);
        return value != null ? value : defaultValue;
    }

    public String getAsString(String configPath, String defaultValue) {
        Object value = get(configPath);
        return value != null ? value.toString() : defaultValue;
    }

    public int getAsInt(String configPath, int defaultValue) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(value.toString());
    }

    public boolean getAsBoolean(String configPath, boolean defaultValue) {
        Object value = get(configPath);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    public long getAsLong(String configPath, long defaultValue) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        } else if (value == null) {
            return defaultValue;
        }
        return Long.parseLong(value.toString());
    }

    public double getAsDouble(String configPath, double defaultValue) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value == null) {
            return defaultValue;
        }
        return Double.parseDouble(value.toString());
    }

    public float getAsFloat(String configPath, float defaultValue) {
        Object value = get(configPath);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        } else if (value == null) {
            return defaultValue;
        }
        return Float.parseFloat(value.toString());
    }

    public <T extends Enum<T>> T getAsEnum(String configPath, Class<T> enumType, T defaultValue) {
        Object value = get(configPath);
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        } else if (value == null) {
            return defaultValue;
        }
        return Enum.valueOf(enumType, value.toString());
    }

    public static ConfigFacade of(@Nonnull ConfigInterface config) {
        return new ConfigFacade(config);
    }
}
