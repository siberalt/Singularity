package com.siberalt.singularity.configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StringMapConfig implements ConfigInterface {
    private final Map<String, Object> config;

    public StringMapConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public Object get(String configPath) {
        return config.get(configPath);
    }

    @Override
    public List<Object> getAsList(String configPath) {
        return config.get(configPath) == null
            ? Collections.emptyList()
            : Collections.unmodifiableList((List<Object>) config.get(configPath));
    }

    @Override
    public Map<String, Object> getAsMap(String configPath) {
        return config.get(configPath) == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap((Map<String, Object>) config.get(configPath));
    }

    @Override
    public boolean has(String configPath) {
        return config.containsKey(configPath);
    }
}
