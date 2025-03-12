package com.siberalt.singularity;

import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

// TODO finish
class ConfigTest {
    @Test
    void testAccess() throws IOException {
        ConfigInterface configuration = new YamlConfig(
                Files.newInputStream(Paths.get("src/test/resources/test-config.yaml"))
        );

//        Assertions.assertNull(configuration.get("run.strategies.definitions.only_up"));
//        Assertions.assertFalse(configuration.has("run.strategies.definitions.only_up"));
//        Assertions.assertTrue(configuration.has("run.strategies.definitions.imoex"));
//
//        Object type = configuration.get("run.strategies.definitions.imoex.@type");
//        Assertions.assertEquals(type.getClass(), String.class);
//        Assertions.assertEquals(type, "imoex");
//
//        type = configuration.get("run.strategies.active");
//        Assertions.assertTrue(type instanceof Iterable<?>);
//
//        type = configuration.get("run.strategies");
//        Assertions.assertTrue(type instanceof ConfigInterface);
//
//        configuration = (ConfigInterface) type;
//        Assertions.assertTrue(configuration.has("definitions.imoex.amount"));
//
//        Object value = configuration.get("definitions.imoex.amount");
//        Assertions.assertEquals(value.getClass(), Integer.class);
//        Assertions.assertEquals(value, 2233);
    }

    @Test
    void testValidation() throws IOException {
        YamlConfig configuration = new YamlConfig(
                Files.newInputStream(Paths.get("src/test/resources/test-config.yaml"))
        );

        // TODO finish
    }
}