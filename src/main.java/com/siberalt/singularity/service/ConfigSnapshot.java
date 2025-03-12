package com.siberalt.singularity.service;

import java.util.Map;

public class ConfigSnapshot {
    private final Map<String, Object> configData;

    public ConfigSnapshot(Map<String, Object> snapshot) {
        this.configData = snapshot;
    }

    public Map<String, Object> getConfigData() {
        return configData;
    }
}
