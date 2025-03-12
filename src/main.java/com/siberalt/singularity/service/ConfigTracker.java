package com.siberalt.singularity.service;

import com.siberalt.singularity.configuration.ConfigInterface;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigTracker implements ConfigInterface {
    private final ConfigInterface config;
    private final Map<String, Object> trackedConfig;

    public ConfigTracker(ConfigInterface config) {
        this.config = config;
        this.trackedConfig = new HashMap<>();
    }

    @Override
    public Object get(String configPath) {
        Object value = config.get(configPath);
        trackedConfig.put(configPath, value);
        return value;
    }

    @Override
    public List<Object> getAsList(String configPath) {
        var value = config.getAsList(configPath);
        trackedConfig.put(configPath, value);
        return value;
    }

    @Override
    public Map<String, Object> getAsMap(String configPath) {
        var value = config.getAsMap(configPath);
        trackedConfig.put(configPath, value);
        return value;
    }

    @Override
    public boolean has(String configPath) {
        return config.has(configPath);
    }

    public ConfigSnapshot getConfigSnapshot() {
        return new ConfigSnapshot(new HashMap<>(trackedConfig));
    }
}
