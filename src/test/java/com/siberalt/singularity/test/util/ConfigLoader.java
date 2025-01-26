package com.siberalt.singularity.test.util;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ConfigLoader {
    public static <T> T load(Class<T> configClass, String settingsPath) throws Exception {
        try (InputStream inputStream = Files.newInputStream(Paths.get(settingsPath))) {
            Yaml yaml = new Yaml();
            return yaml.loadAs(inputStream, configClass);
        }
    }
}
