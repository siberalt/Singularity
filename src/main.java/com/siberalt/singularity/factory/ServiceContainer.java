package com.siberalt.singularity.factory;

import com.siberalt.singularity.shared.IdentifiableInterface;
import com.siberalt.singularity.configuration.ConfigurationInterface;
import com.siberalt.singularity.configuration.validation.ConfigValidationAwareInterface;
import com.siberalt.singularity.configuration.validation.ConstraintsAggregate;
import com.siberalt.singularity.configuration.validation.ValidatorManager;
import com.siberalt.singularity.factory.exception.FactoryNotFoundException;

import java.util.HashMap;
import java.util.Map;

public class ServiceContainer {
    Map<String, FactoryInterface> factories = new HashMap<>();
    ConfigurationInterface configuration;
    String[] serviceConfigMappings = new String[]{};
    Map<String, Object> services = new HashMap<>();
    ValidatorManager validator = new ValidatorManager();

    public ServiceContainer(ConfigurationInterface configuration) {
        this.configuration = configuration;
    }

    public ServiceContainer setConfigMappings(String[] serviceConfigMappings) {
        this.serviceConfigMappings = serviceConfigMappings;

        return this;
    }

    public ValidatorManager getValidator() {
        return validator;
    }

    public ServiceContainer setValidator(ValidatorManager validator) {
        this.validator = validator;
        return this;
    }

    public <T> ServiceContainer add(T service) {
        this.add(service.getClass().getName(), service);

        return this;
    }

    public <T> ServiceContainer add(Class<T> serviceClass, T service) {
        this.add(serviceClass.getName(), service);

        return this;
    }

    public <T> ServiceContainer add(String serviceId, T service) {
        services.put(serviceId, service);

        return this;
    }

    public <T> ServiceContainer addFactory(Class<T> serviceClass, FactoryInterface factory) {
        factories.put(serviceClass.getName(), factory);

        return this;
    }

    public ServiceContainer addFactory(String serviceId, FactoryInterface factory) {
        factories.put(serviceId, factory);

        return this;
    }

    public boolean has(String serviceId) throws FactoryNotFoundException {
        return get(serviceId) != null;
    }

    public <T> boolean has(Class<T> serviceClass) throws FactoryNotFoundException {
        return get(serviceClass) != null;
    }

    public Object get(String serviceId) throws FactoryNotFoundException {
        Object service = services.get(serviceId);

        if (null == service) {
            service = create(serviceId);
            services.put(serviceId, service);
        }

        return service;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceClass) throws FactoryNotFoundException {
        return (T) get(serviceClass.getName());
    }

    public Object create(String serviceId) throws FactoryNotFoundException {
        if (!factories.containsKey(serviceId)) {
            throw new FactoryNotFoundException(serviceId);
        }

        ConfigurationInterface serviceConfig = getConfig(serviceId);
        FactoryInterface factory = factories.get(serviceId);

        if (factory instanceof ConfigValidationAwareInterface) {
            ConstraintsAggregate constraintsAggregate = new ConstraintsAggregate();
            ((ConfigValidationAwareInterface) factory).fillInConstraints(constraintsAggregate);
            validator.validateWithException(serviceConfig, constraintsAggregate);
        }

        Object service = factory.create(serviceConfig, this);

        if (service instanceof IdentifiableInterface) {
            ((IdentifiableInterface) service).setId(serviceId);
        }

        return service;
    }

    public Object create(Class<Object> objectClass) throws FactoryNotFoundException {
        return this.create(objectClass.getName());
    }

    protected ConfigurationInterface getConfig(String serviceId) {
        for (String mapping : serviceConfigMappings) {
            Object config = configuration.get(mapping);

            if (config instanceof ConfigurationInterface configInterface) {
                if (configInterface.has(serviceId)) {
                    return (ConfigurationInterface) configInterface.get(serviceId);
                }
            }
        }

        return null;
    }
}