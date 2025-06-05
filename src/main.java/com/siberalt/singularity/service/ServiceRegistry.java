package com.siberalt.singularity.service;

import com.siberalt.singularity.configuration.*;
import com.siberalt.singularity.service.exception.InvalidServiceTypeException;
import com.siberalt.singularity.service.exception.ServiceDependencyException;
import com.siberalt.singularity.service.exception.ServiceNotFoundException;
import com.siberalt.singularity.service.factory.Factory;
import com.siberalt.singularity.service.factory.ReflectionFactory;

import java.util.HashMap;
import java.util.Map;

public class ServiceRegistry {
    protected Map<String, Definition> serviceDefinitions = new HashMap<>();
    protected Map<String, Object> services = new HashMap<>();
    protected DependencyManager dependencyManager = new DependencyManager(this);
    protected Factory defaultFactory = new ReflectionFactory();

    public void reconfigure(String serviceId, ConfigInterface newConfig) {
        reconfigure(serviceId, newConfig, MergeType.MERGE);
    }

    public <T> void reconfigure(Class<T> serviceClass, ConfigInterface newConfig, MergeType mergeType) {
        reconfigure(serviceClass.getName(), newConfig, mergeType);
    }

    public void reconfigure(String serviceId, ConfigInterface newConfig, MergeType mergeType) {
        if (!services.containsKey(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }

        Definition definition = serviceDefinitions.get(serviceId);
        var service = services.get(serviceId);
        ConfigInterface mergedConfig = new MergedConfig(definition.serviceDetails().config(), newConfig, mergeType);
        ServiceDetails serviceDetails = definition.serviceDetails().updateConfig(mergedConfig);
        definition = definition.updateServiceDetails(serviceDetails);
        serviceDefinitions.put(serviceId, definition);

        boolean configured = configure(service, definition);

        if (!configured) {
            create(definition);
        }
    }

    public Definition getDefinition(String serviceId) {
        return serviceDefinitions.get(serviceId);
    }

    public void setDefinition(String serviceId, Definition definition) {
        if (serviceDefinitions.containsKey(serviceId)) {
            throw new IllegalStateException("Service " + serviceId + " already exists");
        }

        serviceDefinitions.put(serviceId, definition);
    }

    public <T> void set(T service) {
        this.set(service.getClass().getName(), service);
    }

    public <T> void set(Class<T> serviceClass, T service) {
        this.set(serviceClass.getName(), service);
    }

    public <T> void set(Class<T> serviceClass, ConfigInterface config) {
        setDefinition(
            serviceClass.getName(),
            new Definition(new ServiceDetails(serviceClass.getName(), serviceClass, config))
        );
    }

    public <T> void set(String serviceId, T service) {
        services.put(serviceId, service);
        serviceDefinitions.put(
            serviceId,
            new Definition(new ServiceDetails(serviceId, service.getClass(), null))
        );
    }

    public <T> void setFactory(Class<T> serviceClass, Factory factory, ConfigInterface config) {
        setFactory(serviceClass.getName(), factory, config);
    }

    public void setFactory(String serviceId, Factory factory, ConfigInterface config) {
        if (serviceDefinitions.containsKey(serviceId)) {
            var definition = serviceDefinitions.get(serviceId);
            var serviceDetails = definition.serviceDetails();
            serviceDefinitions.put(
                serviceId,
                new Definition(
                    new ServiceDetails(
                        serviceDetails.serviceId(),
                        serviceDetails.serviceClass(),
                        config
                    ),
                    factory,
                    definition.configurator(),
                    definition.configSnapshot()
                )
            );
        } else {
            serviceDefinitions.put(
                serviceId,
                new Definition(
                    new ServiceDetails(serviceId, null, config),
                    factory,
                    null,
                    null
                )
            );
        }
    }

    public <T> void setFactory(Class<T> serviceClass, Factory factory) {
        setFactory(serviceClass.getName(), factory);
    }

    public void setFactory(String serviceId, Factory factory) {
        setFactory(serviceId, factory, new NullConfig());
    }

    public void unset(Class<?> serviceClass) throws ServiceDependencyException {
        unset(serviceClass.getName());
    }

    public void unset(String serviceId) throws ServiceDependencyException {
        if (!services.containsKey(serviceId)) {
            throw new ServiceNotFoundException(serviceId);
        }

        if (dependencyManager.isInUse(serviceId)) {
            throw new ServiceDependencyException(serviceId, dependencyManager.getDependentServices(serviceId));
        }

        services.remove(serviceId);
        serviceDefinitions.remove(serviceId);
    }

    public boolean has(String serviceId) {
        return serviceDefinitions.containsKey(serviceId);
    }

    public <T> boolean has(Class<T> serviceClass) {
        return serviceDefinitions.containsKey(serviceClass.getName());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String serviceId, Class<T> expectedType) {
        Object service = get(serviceId);
        if (null == service) {
            return null;
        }
        if (expectedType.isInstance(service)) {
            return (T) service;
        }

        throw new InvalidServiceTypeException(serviceId, expectedType, service.getClass());
    }

    public Object get(String serviceId) {
        Object service = services.get(serviceId);

        if (null == service) {
            Definition definition = serviceDefinitions.get(serviceId);

            if (null == definition) {
                throw new ServiceNotFoundException(serviceId);
            }

            service = create(definition);
        }

        return service;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceClass) {
        Object service = get(serviceClass.getName());

        if (service == null) {
            return null;
        } else if (!serviceClass.isInstance(service)) {
            throw new InvalidServiceTypeException(serviceClass.getName(), serviceClass, service.getClass());
        }

        return (T) get(serviceClass.getName());
    }

    protected Object create(Definition definition) {
        ServiceDetails serviceDetails = definition.serviceDetails();

        if (null == serviceDetails.serviceClass()) {
            try {
                serviceDetails = serviceDetails.updateServiceClass(Class.forName(serviceDetails.serviceId()));
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (null == serviceDetails.config()) {
            serviceDetails = serviceDetails.updateConfig(new NullConfig());
        }

        dependencyManager.setContextServiceId(serviceDetails.serviceId());

        Object service = null != definition.factory()
            ? definition.factory().create(serviceDetails, dependencyManager)
            : defaultFactory.create(serviceDetails, dependencyManager);

        if (null == serviceDetails.serviceClass()) {
            serviceDetails = serviceDetails.updateServiceClass(service.getClass());
        }

        definition = definition.updateServiceDetails(serviceDetails);
        configure(service, definition);
        services.put(serviceDetails.serviceId(), service);
        serviceDefinitions.put(serviceDetails.serviceId(), definition);

        dependencyManager.resetContextServiceId();

        return service;
    }

    protected boolean configure(Object service, Definition definition) {
        ConfigTracker configTracker = new ConfigTracker(definition.serviceDetails().config());
        ServiceDetails serviceDetails = definition.serviceDetails().updateConfig(configTracker);
        boolean configured = true;

        dependencyManager.setContextServiceId(serviceDetails.serviceId());

        if (service instanceof Configurable) {
            ((Configurable) service).configure(serviceDetails, dependencyManager);
        } else if (definition.factory() instanceof Configurator) {
            ((Configurator) definition.factory()).configure(service, serviceDetails, dependencyManager);
        } else if (null != definition.configurator()) {
            definition.configurator().configure(service, serviceDetails, dependencyManager);
        } else {
            configured = false;
        }

        dependencyManager.resetContextServiceId();

        ConfigSnapshot configSnapshot = configTracker.getConfigSnapshot();
        serviceDefinitions.put(
            serviceDetails.serviceId(),
            new Definition(
                serviceDetails.updateConfig(new MapConfig(configSnapshot.getConfigData())),
                definition.factory(),
                definition.configurator(),
                configSnapshot
            )
        );

        return configured;
    }
}
