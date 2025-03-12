package com.siberalt.singularity.configuration;

import java.util.*;

public class MergedConfig implements ConfigInterface {
    private final ConfigInterface configA;
    private final ConfigInterface configB;
    private final MergeType mergeType;

    public MergedConfig(ConfigInterface configA, ConfigInterface configB, MergeType mergeType) {
        this.configA = configA;
        this.configB = configB;
        this.mergeType = mergeType;
    }

    @Override
    public Object get(String configPath) {
        if (MergeType.MERGE == mergeType) {
            return !configB.has(configPath) ? configA.get(configPath) : configB.get(configPath);
        }

        return configB.get(configPath);
    }

    @Override
    public List<Object> getAsList(String configPath) {
        return switch (mergeType) {
            case MERGE -> {
                var set = new HashSet<>();
                set.addAll(Optional.of(configA.getAsList(configPath)).orElse(Collections.emptyList()));
                set.addAll(Optional.of(configB.getAsList(configPath)).orElse(Collections.emptyList()));
                yield Arrays.asList(set.toArray());
            }
            case REPLACE -> configB.getAsList(configPath);
        };
    }

    @Override
    public Map<String, Object> getAsMap(String configPath) {
        return switch (mergeType) {
            case MERGE -> {
                var map = new HashMap<String, Object>();
                map.putAll(Optional.of(configA.getAsMap(configPath)).orElse(Collections.emptyMap()));
                map.putAll(Optional.of(configB.getAsMap(configPath)).orElse(Collections.emptyMap()));
                yield map;
            }
            case REPLACE -> configB.getAsMap(configPath);
        };
    }

    @Override
    public boolean has(String configPath) {
        return (MergeType.MERGE == mergeType && (configA.has(configPath) || configB.has(configPath))
            || configB.has(configPath));
    }
}
