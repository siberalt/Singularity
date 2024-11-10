package investtech.factory;

import investtech.configuration.ConfigurationInterface;

import java.io.FileNotFoundException;

public interface FactoryInterface {
    Object create(ConfigurationInterface config, ServiceContainer serviceManager);
}
