package com.siberalt.singularity;

import com.siberalt.singularity.broker.impl.tinkoff.execution.TinkoffBroker;
import com.siberalt.singularity.configuration.validation.ValidatorManager;
import com.siberalt.singularity.configuration.validation.constraints.RangeConstraint;
import com.siberalt.singularity.configuration.validation.constraints.RequiredConstraint;
import com.siberalt.singularity.configuration.validation.constraints.TypesConstraint;
import com.siberalt.singularity.configuration.validation.validators.RangeValidator;
import com.siberalt.singularity.configuration.validation.validators.RequiredValidator;
import com.siberalt.singularity.configuration.validation.validators.TypesValidator;
import com.siberalt.singularity.factory.ServiceContainer;
import com.siberalt.singularity.factory.broker.TinkoffBrokerFactory;
import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.configuration.YamlConfiguration;
import com.siberalt.singularity.factory.exception.FactoryNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ServiceContainerTest {
    @Test
    void test() throws IOException, FactoryNotFoundException {
        ConfigurationInterface configuration = new YamlConfiguration(
            Files.newInputStream(Paths.get("src/test/resources/test-services.yaml"))
        );

        ServiceContainer serviceContainer = new ServiceContainer(configuration)
            .setConfigMappings(new String[]{"brokers"})
            .setValidator(
                new ValidatorManager()
                    .add(RangeConstraint.class, new RangeValidator())
                    .add(TypesConstraint.class, new TypesValidator())
                    .add(RequiredConstraint.class, new RequiredValidator())
            )
            .add(configuration)
            .add(ConfigurationInterface.class, configuration)
            .add("yaml_config", configuration)
            .addFactory("tinkoff", new TinkoffBrokerFactory());

        Assertions.assertTrue(serviceContainer.has(ConfigurationInterface.class));
        Assertions.assertTrue(serviceContainer.has(YamlConfiguration.class));
        Assertions.assertTrue(serviceContainer.has("yaml_config"));

        Assertions.assertTrue(serviceContainer.get(ConfigurationInterface.class) instanceof YamlConfiguration);
        Assertions.assertNotNull(serviceContainer.get(YamlConfiguration.class));
        Assertions.assertTrue(serviceContainer.get("yaml_config") instanceof YamlConfiguration);

        Assertions.assertTrue(serviceContainer.get("tinkoff") instanceof TinkoffBroker);
    }
}
