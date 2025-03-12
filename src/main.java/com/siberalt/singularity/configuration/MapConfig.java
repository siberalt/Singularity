package com.siberalt.singularity.configuration;

import java.util.List;
import java.util.Map;

public class MapConfig implements ConfigInterface {
    private final Map<String, Object> config;

    public MapConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object get(String configPath) {
        Map<String, Object> configClone = config;
        Object value = null;
        String[] keys = configPath.split("\\.");
        int iteration = 0;

        for (String key : keys) {
            value = configClone.get(key);
            iteration++;

            if (!(value instanceof Map)) {
                break;
            }

            configClone = (Map<String, Object>) value;
        }

        return keys.length == iteration ? value : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Object> getAsList(String configPath) {
        Object value = get(configPath);
        if (value instanceof List) {
            return (List<Object>) value;
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAsMap(String configPath) {
        Object value = get(configPath);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    public boolean has(String configPath) {
        return null != get(configPath);
    }
}
