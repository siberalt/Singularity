package com.siberalt.singularity.configuration;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class YamlConfig extends MapConfig {
    public YamlConfig(InputStream stream) {
        super (new Yaml().load(stream));
    }

    public YamlConfig(String config){
        super(new Yaml().load(config));
    }
}
