package com.siberalt.singularity.configuration;

import java.util.List;
import java.util.Map;

public class NullConfig implements ConfigInterface {
    @Override
    public Object get(String configPath) {
        return null;
    }

    @Override
    public List<Object> getAsList(String configPath) {
        return null;
    }

    @Override
    public Map<String, Object> getAsMap(String configPath) {
        return null;
    }

    @Override
    public boolean has(String configPath) {
        return false;
    }
}
