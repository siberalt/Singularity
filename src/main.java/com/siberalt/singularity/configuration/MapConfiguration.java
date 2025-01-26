package com.siberalt.singularity.configuration;

import java.util.Map;

public class MapConfiguration implements ConfigurationInterface {
    Map<String, Object> config;

    String parentPath;

    public MapConfiguration(Map<String, Object> config, String parentPath) {
        this.config = config;
        this.parentPath = parentPath;
    }

    public MapConfiguration(Map<String, Object> config) {
        this.config = config;
    }

    public Object get(String configPath) {
        Map<?, ?> configClone = config;
        Object value = null;
        String[] keys = configPath.split("\\.");
        int iteration = 0;

        for (String key : keys) {
            value = configClone.get(key);
            iteration++;

            if (value instanceof Map) {
                configClone = (Map) value;
            } else {
                break;
            }

        }
        // TODO fix that
        if (keys.length == iteration) {
            if (value instanceof Map) {
                return new MapConfiguration(safelyCastToMap(value), configPath);
            }
        } else {
            value = null;
        }

        return value;
    }

    public Map<String, Object> safelyCastToMap(Object object) {
        if (object instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) object;
            // Проверяем, что все ключи и значения соответствуют требуемым типам
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    throw new ClassCastException("Map contains invalid keys or values.");
                }
            }

            return (Map<String, Object>) map;
        } else {
            throw new ClassCastException("Provided object is not a Map.");
        }
    }

    public boolean has(String configPath) {
        return null != get(configPath);
    }

    @Override
    public String getFullConfigPath(String configPath) {
        return null == parentPath ? configPath : parentPath + "." + configPath;
    }
}
