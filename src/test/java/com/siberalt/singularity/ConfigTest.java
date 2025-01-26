package com.siberalt.singularity;

import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.configuration.YamlConfiguration;
import com.siberalt.singularity.configuration.exception.InvalidConfigException;
import com.siberalt.singularity.configuration.validation.Error;
import com.siberalt.singularity.configuration.validation.*;
import com.siberalt.singularity.configuration.validation.constraints.RangeConstraint;
import com.siberalt.singularity.configuration.validation.constraints.RequiredConstraint;
import com.siberalt.singularity.configuration.validation.constraints.TypesConstraint;
import com.siberalt.singularity.configuration.validation.validators.RangeValidator;
import com.siberalt.singularity.configuration.validation.validators.RequiredValidator;
import com.siberalt.singularity.configuration.validation.validators.TypesValidator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

class ConfigTest {
    @Test
    void testAccess() throws IOException {
        ConfigurationInterface configuration = new YamlConfiguration(
                Files.newInputStream(Paths.get("src/test/resources/test-config.yaml"))
        );

        Assertions.assertNull(configuration.get("run.strategies.definitions.only_up"));
        Assertions.assertFalse(configuration.has("run.strategies.definitions.only_up"));
        Assertions.assertTrue(configuration.has("run.strategies.definitions.imoex"));

        Object type = configuration.get("run.strategies.definitions.imoex.@type");
        Assertions.assertEquals(type.getClass(), String.class);
        Assertions.assertEquals(type, "imoex");

        type = configuration.get("run.strategies.active");
        Assertions.assertTrue(type instanceof Iterable<?>);

        type = configuration.get("run.strategies");
        Assertions.assertTrue(type instanceof ConfigurationInterface);

        configuration = (ConfigurationInterface) type;
        Assertions.assertTrue(configuration.has("definitions.imoex.amount"));

        Object value = configuration.get("definitions.imoex.amount");
        Assertions.assertEquals(value.getClass(), Integer.class);
        Assertions.assertEquals(value, 2233);
        Assertions.assertEquals(
                configuration.getFullConfigPath("definitions.imoex.amount"),
                "run.strategies.definitions.imoex.amount"
        );
    }

    @Test
    void testValidation() throws IOException {
        YamlConfiguration configuration = new YamlConfiguration(
                Files.newInputStream(Paths.get("src/test/resources/test-config.yaml"))
        );

        ConstraintsAggregate aggregate = new ConstraintsAggregate()
                .add("run.strategies.definitions.only_up", ConstraintsAggregate.of(true))
                .add("run.strategies.definitions.imoex.@type", ConstraintsAggregate.of(true, ValueType.STRING))
                .add("run.strategies.definitions.amount", ConstraintsAggregate.of(ValueType.INTEGER))
                .add("run.strategies.definitions.imoex.@type", new RangeConstraint(new Object[]{"imoex", "only_up"}))
                .add("run.strategies.definitions.imoex.amount", ConstraintsAggregate.of(ValueType.BOOLEAN))
                .add("run.strategies.definitions.imoex.amount", ConstraintsAggregate.of(ValueType.SET))
                .add("run.strategies.definitions.imoex.amount", ConstraintsAggregate.of(ValueType.INTEGER));

        ValidatorManager validatorManager = new ValidatorManager()
                .add(RangeConstraint.class, new RangeValidator())
                .add(TypesConstraint.class, new TypesValidator())
                .add(RequiredConstraint.class, new RequiredValidator());

        Assertions.assertThrows(
                InvalidConfigException.class,
                () -> validatorManager.validateWithException(configuration, aggregate)
        );

        ValidationResult result = validatorManager.validate(configuration, aggregate);

        String[] actualErrors = result
                .getErrors()
                .stream()
                .map(com.siberalt.singularity.configuration.validation.Error::getConfigPath)
                .sorted()
                .toArray(String[]::new);

        Assertions.assertArrayEquals(
                Arrays.stream(new String[]{
                        "run.strategies.definitions.imoex.amount",
                        "run.strategies.definitions.only_up",
                        "run.strategies.definitions.imoex.amount"
                }).sorted().toArray(String[]::new),
                actualErrors
        );

        ConstraintsAggregate aggregate1 = new ConstraintsAggregate()
                .add("imoex.@type", ConstraintsAggregate.of(ValueType.BOOLEAN))
                .add("imoex.cringe", ConstraintsAggregate.of(true));

        result = validatorManager.validate((ConfigurationInterface) configuration.get("run.strategies.definitions"), aggregate1);

        actualErrors = result
                .getErrors()
                .stream()
                .map(Error::getConfigPath)
                .sorted()
                .toArray(String[]::new);

        Assertions.assertArrayEquals(
                Arrays.stream(new String[]{
                        "run.strategies.definitions.imoex.@type",
                        "run.strategies.definitions.imoex.cringe",
                }).sorted().toArray(String[]::new),
                actualErrors
        );
    }
}