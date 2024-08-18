package investtech;

import investtech.broker.impl.tinkoff.run.TinkoffBroker;
import investtech.configuration.ConfigurationInterface;
import investtech.configuration.YamlConfiguration;
import investtech.factory.ServiceContainer;
import investtech.factory.broker.TinkoffBrokerFactory;
import investtech.factory.exception.FactoryNotFoundException;
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
                .add(configuration)
                .add(ConfigurationInterface.class, configuration)
                .add("yaml_config", configuration)
                .addFactory(TinkoffBroker.class, new TinkoffBrokerFactory())
                .addFactory("tinkoff", new TinkoffBrokerFactory());

        Assertions.assertTrue(serviceContainer.has(ConfigurationInterface.class));
        Assertions.assertTrue(serviceContainer.has(YamlConfiguration.class));
        Assertions.assertTrue(serviceContainer.has("yaml_config"));

        Assertions.assertTrue(serviceContainer.get(ConfigurationInterface.class) instanceof YamlConfiguration);
        Assertions.assertTrue(serviceContainer.get(YamlConfiguration.class) instanceof YamlConfiguration);
        Assertions.assertTrue(serviceContainer.get("yaml_config") instanceof YamlConfiguration);

        Assertions.assertTrue(serviceContainer.get(TinkoffBroker.class) instanceof TinkoffBroker);
        Assertions.assertTrue(serviceContainer.get("tinkoff") instanceof TinkoffBroker);
    }
}
